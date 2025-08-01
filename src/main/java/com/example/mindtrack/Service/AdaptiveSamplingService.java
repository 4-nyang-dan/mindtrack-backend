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

    private static final double SIMILARITY_THRESHOLD = 0.9;

    public ResponseEntity<Map<String, Object>> processImageSampling(String userId, BufferedImage image) throws IOException {
        Map<String, Object> response = new HashMap<>();

        Users user = userRepository.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        String newHash = similarityCheckService.computeHash(image);
        Optional<ScreenshotImage> lastSaved = screenshotImageRepository.findTopByUser_IdOrderByLastVisitedAtDesc(user.getId());

        try{

            if(lastSaved.isPresent()){
                String prevS3url = lastSaved.get().getS3url();
                String filePath = prevS3url.replace("http://localhost:8080/", "");

                BufferedImage prevImage =  ImageIO.read(new File(filePath));
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
                    // 직전 저장 이미지와의 유사도가 낮을 때 -> 이전 저장된 적이 있었다면
                    Optional<ScreenshotImage> optional = screenshotImageRepository.findTopByUser_IdAndImageHash(user.getId(), newHash);

                    if(optional.isPresent()){
                        // 저장된 적이 있다면, ssim이 높을때 방문도 + 1 -> 해시는 같은데 실제로 유사 하지 않은 경우가 있어서
                        String prevSameHashS3url = optional.get().getS3url();
                        String prevSameHashFilePath = prevSameHashS3url.replace("http://localhost:8080/", "");

                        BufferedImage prevSameHashImage = ImageIO.read(new File(prevSameHashFilePath));
                        double reSimilarity = similarityCheckService.computeSimilarity(image, prevSameHashImage);
                        if(reSimilarity >= SIMILARITY_THRESHOLD){
                            ScreenshotImage prevScreenshotImage = optional.get();
                            prevScreenshotImage.updateLastVisited(LocalDateTime.now());

                            screenshotImageRepository.save(prevScreenshotImage);

                            // AI 재분석 요청(일단 로그만)
                            return ResponseEntity.ok(Map.of(
                                    "success", true,
                                    "similarity", reSimilarity,
                                    "prevImageId", optional.get().getId(),
                                    "message", "저장된 적 있음! AI 재분석 요청: " + prevScreenshotImage.getS3url() + ", visitCnt=" + prevScreenshotImage.getVisitCnt()
                            ));
                        }else{
                            response.put("prevImageId", optional.get().getId());
                            response.put("message", "이전에 저장된 비슷한 이미지가 있지만(새 이미지 newHash와 같은 값이 있음), 실제로 구조가 다름(유사도SSIM가 낮음)");
                        }
                    }else{
                        response.put("message", "이전에 저장된 역사가 없음, 새 이미지 newHash와 같은 값이 아예 없음");
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

    private ResponseEntity<Map<String, Object>> saveScreenshotImage(BufferedImage image, Users user, String newHash, Map<String, Object> response) throws IOException {
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

        // AI 분석 요청(일단 로그만)
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
