package com.example.mindtrack.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import com.example.mindtrack.DTO.AnalysisResultDto;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/analysis")
public class AnalysisController {

    @PostMapping("/result")
    public ResponseEntity<String> receiveResult(@RequestBody AnalysisResultDto result) {
        // 결과 저장 (DB에 넣거나 서비스로 전달)
        log.info("받은 분석 결과: userId={}, imageId={}", result.getUserId(), result.getImageId());
        return ResponseEntity.ok("결과 수신 완료");

    }
}
