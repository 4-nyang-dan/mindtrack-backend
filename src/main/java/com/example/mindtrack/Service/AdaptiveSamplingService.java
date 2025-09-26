package com.example.mindtrack.Service;

import com.example.mindtrack.Util.CustomException;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdaptiveSamplingService {

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

        // 1) Redis 후보 이미지 찾기 (2차 샘플링)
        // 이미지의 해시를 계산한다.
        long newHash = similarityCheckService.computeHash(image);

        try {
            // 지금 들어온 이미지의 dHash 해시값과 레디스 캐시 값의 해밍거리를 계산하여 유사한 해시값을 가져옴
            Optional<ScreenshotImageCacheService.Candidate> mostSimilarFromCache = screenshotImageCacheService
                    .findMostSimilarFromCache(user.getId(), newHash, 6);

            // 2) 후보 이미지가 존재한다면 → 썸네일 가져와서 SSIM 유사도 재검증
            if (mostSimilarFromCache.isPresent()) {
                // DB 가 아닌 Redis 에서 썸네일 가져오기
                Optional<byte[]> thumbBytesOpt = screenshotImageCacheService.getImageThumbByHash(user.getId(),
                        mostSimilarFromCache.get().hash());

                if (thumbBytesOpt.isPresent()) {
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

                        // 이전 썸네일
                        BufferedImage prevThumb = ImageIO.read(bais);
                        double reSimilarity = similarityCheckService.computeSimilarity(image, prevThumb);

                        // 그 유사도가 높다면, 거의 일치하는 이미지라고 판단
                        // 방문도를 높이고, 최근 방문 시간도 업데이트한다. <---- 방문도 관련 코드 제거
                        if (reSimilarity >= SIMILARITY_THRESHOLD) {
                            Long prevImageId = mostSimilarFromCache.get().imageId();
                            boolean isProcessing = screenshotImageCacheService.isProcessing(user.getId(), prevImageId);

                            if (isProcessing) {
                                // 처리 중이라면 → 새 이미지로 저장
                                response.put("parentImageId", prevImageId);
                                return saveScreenshotImage(image, user, newHash, response);
                            }

                            else { // pending 상태라면 Redis에 덮어쓰기
                                   // Redis에 저장(캐시 추가)
                                   // 방금 들어온 "원본"을 기존 이미지ID 키로 Redis에 덮어쓰기
                                byte[] originalBytesForReanalysis = imageToBytes(image);
                                screenshotImageCacheService.cacheOriginalImage(user.getId(),
                                        mostSimilarFromCache.get().imageId(), originalBytesForReanalysis);

                                log.info("[재분석 예약] userId={}, prevImageId={}, status=PENDING 로 전환 + Redis 원본 덮어쓰기 완료",
                                        user.getId(), mostSimilarFromCache.get().imageId());

                                // AI 재분석 요청 결과 반환
                                return ResponseEntity.ok(Map.of(
                                        "success", true,
                                        "similarity", reSimilarity,
                                        "prevImageId", mostSimilarFromCache.get().imageId(),
                                        "message", "저장된 적 있음! AI 재분석 요청"));
                            }
                        } else {
                            response.put("prevImageId", mostSimilarFromCache.get().imageId());
                            response.put("message",
                                    "이전에 저장된 비슷한 이미지가 있지만(새 이미지 newHash와 유사도 9.7 이상 혹은 해밍거리 6이하가 있음), 실제로 구조가 다름(유사도SSIM가 낮음)");
                        }
                    }

                } else {
                    response.put("message", "이전에 저장된 역사가 없음, 새 이미지 newHash와 같은 값이 아예 없음");
                }
            }
            // 신규 이미지 처리
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

        // === 신규 이미지 Redis 저장====
        // Redis에 저장(캐시 추가) -> 최근 리스트 + 썸네일(TTL) 저장
        Long newImageId = screenshotImageCacheService.generateNewImageId(user.getId());

        screenshotImageCacheService.cacheRecentImageHash(user.getId(), newImageId, newHash, thumbBytes);
        screenshotImageCacheService.cacheOriginalImage(user.getId(), newImageId, originalBytes);

        response.put("currentImageId", newImageId);
        response.put("message", "새 이미지 Redis 저장 완료");

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}