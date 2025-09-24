package com.example.mindtrack.Service;

import com.example.mindtrack.Util.CustomException;
import com.example.mindtrack.Controller.AuthController;
import com.example.mindtrack.Domain.ScreenshotImage;
import com.example.mindtrack.Domain.Users;
import com.example.mindtrack.Enum.AnalysisStatus;
import com.example.mindtrack.Repository.ScreenshotImageRepository;
import com.example.mindtrack.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

// ===========  AI 요청 재분석시 패딩 작업 추가  ============
// prevImageId로 Redis 원본을 덮어써야 워커가 DB에서 
// 그 행을 PENDING으로 집었을 때, 정확한 원본을 읽어 분석할 수 있음
@Service
@Slf4j
@RequiredArgsConstructor
public class AdaptiveSamplingService {

    private final ScreenshotImageRepository screenshotImageRepository;
    private final UserRepository userRepository;
    private final SimilarityCheckService similarityCheckService;
    private final ScreenshotImageCacheService screenshotImageCacheService;

    private static final double SIMILARITY_THRESHOLD = 0.85;

    /**
     * 받은 이미지의 샘플링을 진행한다.
     * 
     * @param userId
     * @param image
     * @return
     * @throws IOException
     */
    public ResponseEntity<Map<String, Object>> processImageSampling(String userId, BufferedImage image) {
        Map<String, Object> response = new HashMap<>();

        // 유저아이디를 통해 유저를 찾는다
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 이미지의 해시를 계산한다.
        long newHash = similarityCheckService.computeHash(image);

        try {
            // 1차 샘플링 구간 제거
            // 2차 샘플링 구간 부터
            //
            // 직전 저장 이미지와의 유사도가 낮을 때

            // 지금 들어온 이미지의 dHash 해시값과 레디스 캐시 값의 해밍거리를 계산하여 유사한 해시값을 가져옴
            Optional<ScreenshotImageCacheService.Candidate> mostSimilarFromCache = screenshotImageCacheService
                    .findMostSimilarFromCache(user.getId(), newHash, 6);

            if (mostSimilarFromCache.isPresent()) {
                // 있다면, 그 해시값으로 해당 이미지 ScreenshotImage 가져오기
                Optional<ScreenshotImage> ScreenshotImageOpt = screenshotImageRepository
                        .findTopByUser_IdAndId(user.getId(), mostSimilarFromCache.get().imageId());
                // 그리고 레디스 캐시에서 해당 해시 키를 가진 값(축소판 이미지)을 가져옴
                Optional<byte[]> thumbBytesOpt = screenshotImageCacheService.getImageThumbByHash(user.getId(),
                        mostSimilarFromCache.get().hash());

                if (ScreenshotImageOpt.isPresent() && thumbBytesOpt.isPresent()) {
                    // 3차 샘플링 구간
                    //
                    // 하지만, 해시 값이 같지만(dHash 계산으론 유사한 이미지이지만), 실제로 유사하지 않은 구조일 확률도 있으므로(타이핑이 더 됐다는
                    // 등),
                    // 한 번 더 같은 해시값으로 가져와진 이미지와 지금 들어온 이미지와 SSIM 유사도 계산을 진행

                    // String prevSameHashS3url = optional.get().getS3url();
                    // String prevSameHashFilePath =
                    // prevSameHashS3url.replace("http://localhost:8080/", "");
                    // BufferedImage prevSameHashImage = ImageIO.read(new
                    // File(prevSameHashFilePath));

                    // 레디스 캐시에서 가져온 byte를 다시 이미지로 변환
                    byte[] thumbBytes = thumbBytesOpt.get();
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(thumbBytes)) {
                        BufferedImage prevThumb = ImageIO.read(bais);
                        double reSimilarity = similarityCheckService.computeSimilarity(image, prevThumb);

                        // 그 유사도가 높다면, 거의 일치하는 이미지라고 판단
                        // 방문도를 높이고, 최근 방문 시간도 업데이트한다.

                        /*
                         * 재분석 트리거 분기 (수정사항):
                         * 재유사도가 기준 이상이면 "같은 장면"으로 간주하고, 기존 이미지를 재사용한다.
                         * 이때 반드시 "워커가 집어가기 전에 (= PENDING 선점 전에 ) Redis 에 원본을 먼저 덮어 쓰워야"
                         * 워커가 [원본 이미지 없음] 과 같은 문제가 생기지 않고 안전하게 PENDING 이미지를 집어갈 수 있음
                         * 
                         */
                        if (reSimilarity >= SIMILARITY_THRESHOLD) {
                            ScreenshotImage prevScreenshotImage = ScreenshotImageOpt.get();

                            // 1) 최근 방문 시간 및 방문도를 높인다.
                            prevScreenshotImage.updateLastVisited(LocalDateTime.now());

                            // 2) Redis 에 "원본"을 먼저 덮어 쓴다. (워커가 즉시 선점하더라도 원본 준비 상태 확보)
                            byte[] originalBytesForReanalysis = imageToBytes(image); // 이미 있는 유틸 재사용
                            screenshotImageCacheService.cacheOriginalImage(
                                    user.getId(),
                                    prevScreenshotImage.getId(),
                                    // prevScreenshotImage.getImageHash()
                                    originalBytesForReanalysis);

                            log.info("[재분석 준비완료] Redis 원본 저장 userId={} imageId={} bytes={}",
                                    user.getId(), prevScreenshotImage.getId(), originalBytesForReanalysis.length);

                            // (3) 이제 DB 상태를 PENDING으로 전환 → 워커가 집어감
                            prevScreenshotImage.updateResult(null, AnalysisStatus.PENDING);
                            screenshotImageRepository.save(prevScreenshotImage);
                            log.info("[재분석 예약] DB 상태 PENDING 전환 userId={} imageId={}",
                                    user.getId(), prevScreenshotImage.getId());

                            // 4) 응답 : AI 재분석 요청
                            return ResponseEntity.ok(Map.of(
                                    "success", true,
                                    "similarity", reSimilarity,
                                    "prevImageId", ScreenshotImageOpt.get().getId(),
                                    "message", "저장된 적 있음! AI 재분석 요청: visitCnt=" + prevScreenshotImage.getVisitCnt()));
                        } else {
                            response.put("prevImageId", ScreenshotImageOpt.get().getId());
                            response.put("message",
                                    "이전에 저장된 비슷한 이미지가 있지만(새 이미지 newHash와 유사도 9.7 이상 혹은 해밍거리 6이하가 있음), 실제로 구조가 다름(유사도SSIM가 낮음)");
                        }
                    }

                } else {
                    response.put("message", "이전에 저장된 역사가 없음, 새 이미지 newHash와 같은 값이 아예 없음");
                }
            }
            return saveScreenshotImage(image, user, newHash, response);

        } catch (Exception e) {
            throw new CustomException("에러 발생: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

    }

    // AWS S3 SDK 사용, 현재는 로컬 저장으로 구현되어있음
    // AWS 연동 이후 AmazonS3Clinet.putObject()식으로 변경
    /*
     * private String s3UploadAndReturnURL(BufferedImage image, String user_id)
     * throws IOException {
     * String fileName = user_id + "_" + UUID.randomUUID() + ".png";
     * File file = new File("uploads/" + fileName);
     * file.getParentFile().mkdirs(); // 폴더 없으면 생성
     * ImageIO.write(image, "png", file);
     * 
     * return "http://localhost:8080/uploads/" + fileName;
     * }
     */

    private byte[] imageToBytes(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 썸네일(축소/압축) 바이트 생성 – 메모리 절약용
    private byte[] toThumbnailBytes(BufferedImage src) throws IOException {
        int targetW = 480; // 필요에 맞게 조정 가능
        int w = src.getWidth(), h = src.getHeight();
        int newW = Math.max(1, targetW);
        int newH = Math.max(1, (int) Math.round((double) h * newW / Math.max(1, w)));

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // PNG보다 용량이 작은 JPG로 설정
            ImageIO.write(scaled, "jpg", baos);
            return baos.toByteArray();
        }
    }

    private ResponseEntity<Map<String, Object>> saveScreenshotImage(BufferedImage image, Users user, Long newHash,
            Map<String, Object> response) throws IOException {
        // String s3Url = s3UploadAndReturnURL(image, user.getUserId());
        byte[] originalBytes = imageToBytes(image); // 원본 바이트
        byte[] thumbBytes = toThumbnailBytes(image); // 썸네일 바이트

        ScreenshotImage newScreenshot = ScreenshotImage.builder()
                .user(user)
                .imageHash(newHash)
                .visitCnt(1)
                .capturedAt(LocalDateTime.now())
                .lastVisitedAt(LocalDateTime.now())
                .analysisStatus(AnalysisStatus.PENDING)
                .build();

        screenshotImageRepository.save(newScreenshot);

        // Redis에 저장(캐시 추가) -> 최근 리스트 + 썸네일(TTL) 저장
        screenshotImageCacheService.cacheRecentImageHash(
                user.getId(),
                newScreenshot.getId(),
                newScreenshot.getImageHash(),
                thumbBytes);

        // 원본(TTL) 저장 – FastAPI 워커가 여기서 읽어감
        screenshotImageCacheService.cacheOriginalImage(
                user.getId(),
                newScreenshot.getId(),
                originalBytes);

        response.put("currentImageId", newScreenshot.getId());

        // AI 분석 요청(일단 로그만)
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}