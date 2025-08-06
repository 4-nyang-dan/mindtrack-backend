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
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdaptiveSamplingService {

    private final ScreenshotImageRepository screenshotImageRepository;
    private final UserRepository userRepository;
    private final SimilarityCheckService similarityCheckService;
    private final ScreenshotImageCacheService screenshotImageCacheService;

    private static final double SIMILARITY_THRESHOLD = 0.9;

    /**
     * 받은 이미지의 샘플링을 진행한다.
     * @param userId
     * @param image
     * @return
     * @throws IOException
     */
    public ResponseEntity<Map<String, Object>> processImageSampling(String userId, BufferedImage image) throws IOException {
        Map<String, Object> response = new HashMap<>();

        // 유저아이디를 통해 유저를 찾는다
        Users user = userRepository.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 이미지의 해시를 계산한다.
        long newHash = similarityCheckService.computeHash(image);
        log.info(">> 최근 들어온 이미지 hash 값 = {}", newHash);

        // 바로 직전 최근에 저장된 사진을 가져온다.
        Optional<ScreenshotImage> lastSaved = screenshotImageRepository.findTopByUser_IdOrderByLastVisitedAtDesc(user.getId());

        try{

            // 1차 샘플링 구간
            // 바로 직전 저장된 사진이 존재한다면,
            if(lastSaved.isPresent()){
                // 이미지 원본을 가져오고 (실제론 아마존 S3에서 가져옴, 지금은 테스트로 로컬에 저장된걸 가져옴)
                String prevS3url = lastSaved.get().getS3url();
                String filePath = prevS3url.replace("http://localhost:8080/", "");
                BufferedImage prevImage =  ImageIO.read(new File(filePath));

                // 직전 이미지와의 유사도 계산
                double similarity = similarityCheckService.computeSimilarity(image, prevImage);

                // 직전 저장 이미지와의 유사도가 높을 때 -> 연속 같은 이미지라 판단, 방문도를 높이지 않음
                if(similarity >= SIMILARITY_THRESHOLD) {
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "similarity", similarity,
                            "prevImageId", lastSaved.get().getId(),
                            "message", "연속된 동일 이미지는 무시됨"
                    ));
                }else{
                    // 2차 샘플링 구간
                    //
                    // 직전 저장 이미지와의 유사도가 낮을 때

                    // 지금 들어온 이미지의 dHash 해시값과 레디스 캐시 값의 해밍거리를 계산하여 유사한 해시값을 가져옴
                    Optional<Long> mostSimilarFromCache = screenshotImageCacheService.findMostSimilarFromCache(user.getId(), newHash, 6);

                    if(mostSimilarFromCache.isPresent()){
                        // 있다면, 그 해시값으로 해당 이미지 ScreenshotImage 가져오기
                        Optional<ScreenshotImage> optional = screenshotImageRepository.findTopByUser_IdAndId(user.getId(), mostSimilarFromCache.get());

                        if(optional.isPresent()){
                            // 3차 샘플링 구간
                            //
                            // 하지만, 해시 값이 같지만(dHash 계산으론 유사한 이미지이지만), 실제로 유사하지 않은 구조일 확률도 있으므로(타이핑이 더 됐다는 등),
                            // 한 번 더 같은 해시값으로 가져와진 이미지와 지금 들어온 이미지와 SSIM 유사도 계산을 진행
                            String prevSameHashS3url = optional.get().getS3url();
                            String prevSameHashFilePath = prevSameHashS3url.replace("http://localhost:8080/", "");
                            BufferedImage prevSameHashImage = ImageIO.read(new File(prevSameHashFilePath));
                            double reSimilarity = similarityCheckService.computeSimilarity(image, prevSameHashImage);

                            // 그 유사도가 높다면, 거의 일치하는 이미지라고 판단
                            // 방문도를 높이고, 최근 방문 시간도 업데이트한다.
                            if(reSimilarity >= SIMILARITY_THRESHOLD){
                                ScreenshotImage prevScreenshotImage = optional.get();

                                // 최근 방문 시간 및 방문도를 높인다.
                                prevScreenshotImage.updateLastVisited(LocalDateTime.now());
                                screenshotImageRepository.save(prevScreenshotImage);

                                // Redis에 저장(캐시 추가)
                                screenshotImageCacheService.cacheRecentImageHash(
                                        user.getId(),
                                        prevScreenshotImage.getId(),
                                        prevScreenshotImage.getImageHash()
                                );

                                // AI 재분석 요청
                                return ResponseEntity.ok(Map.of(
                                        "success", true,
                                        "similarity", reSimilarity,
                                        "prevImageId", optional.get().getId(),
                                        "message", "저장된 적 있음! AI 재분석 요청: " + prevScreenshotImage.getS3url() + ", visitCnt=" + prevScreenshotImage.getVisitCnt()
                                ));
                            }else{
                                response.put("prevImageId", optional.get().getId());
                                response.put("message", "이전에 저장된 비슷한 이미지가 있지만(새 이미지 newHash와 유사도 9.7 이상 혹은 해밍거리 6이하가 있음), 실제로 구조가 다름(유사도SSIM가 낮음)");
                            }
                        }else{
                            response.put("message", "이전에 저장된 역사가 없음, 새 이미지 newHash와 같은 값이 아예 없음");
                        }
                    }


                }
            }
            return saveScreenshotImage(image, user, newHash, response);

        }catch (Exception e){
            throw new CustomException("에러 발생: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

    }

    // AWS S3 SDK 사용
    // AWS 연동 이후 AmazonS3Clinet.putObject()식으로 변경
    private String s3UploadAndReturnURL(BufferedImage image, String user_id) throws IOException {
        String fileName = user_id + "_" + UUID.randomUUID() + ".png";
        File file = new File("uploads/" + fileName);
        file.getParentFile().mkdirs(); // 폴더 없으면 생성
        ImageIO.write(image, "png", file);

        return "http://localhost:8080/uploads/" + fileName;
    }

    private ResponseEntity<Map<String, Object>> saveScreenshotImage(BufferedImage image, Users user, Long newHash, Map<String, Object> response) throws IOException {
        String s3Url = s3UploadAndReturnURL(image, user.getUserId());

        ScreenshotImage newScreenshot = ScreenshotImage.builder()
                .user(user)
                .imageHash(newHash)
                .visitCnt(1)
                .s3url(s3Url)
                .capturedAt(LocalDateTime.now())
                .lastVisitedAt(LocalDateTime.now())
                .analysisStatus(AnalysisStatus.PENDING)
                .build();

        screenshotImageRepository.save(newScreenshot);

        // Redis에 저장(캐시 추가)
        screenshotImageCacheService.cacheRecentImageHash(
                user.getId(),
                newScreenshot.getId(),
                newScreenshot.getImageHash()
        );

        response.put("currentImageId", newScreenshot.getId());

        // AI 분석 요청(일단 로그만)
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
