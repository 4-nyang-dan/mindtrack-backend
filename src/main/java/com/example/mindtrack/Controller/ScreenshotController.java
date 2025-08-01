package com.example.mindtrack.Controller;

import com.example.mindtrack.Service.AdaptiveSamplingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ScreenshotController {

    private final AdaptiveSamplingService samplingService;

    @PostMapping(value = "/upload-screenshot", consumes = "multipart/form-data")
    @Operation(summary = "스크린샷 업로드 및 샘플링 처리")
    public ResponseEntity<Map<String, Object>> upload(
            @Parameter(description = "업로드할 스크린샷 이미지", required = true) @RequestParam("image") MultipartFile image,
            @Parameter(description = "사용자 ID", required = true) @RequestParam("userId") String userId
    ) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(image.getInputStream());
        return samplingService.processImageSampling(userId, bufferedImage);
    }
}
