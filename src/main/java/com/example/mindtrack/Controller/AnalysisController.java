package com.example.mindtrack.Controller;

import com.example.mindtrack.DTO.AnalysisResultDto;
import com.example.mindtrack.Service.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/analysis")
public class AnalysisController {

    private final SuggestionService suggestionService;

    @PostMapping("/result")
    public ResponseEntity<String> receiveResult(@RequestBody Map<String, Object> payload) {
        // 받은 페이로드 전체를 로그로 출력
        log.info("받은 분석 결과 전체: {}", payload);

        // 특정 필드만 보고 싶으면 Map에서 꺼내서 사용 가능
        Object userId = payload.get("userId");
        Object imageIds = payload.get("imageIds");
        log.info("userId={}, imageIds={}", userId, imageIds);

        return ResponseEntity.ok("결과 수신 완료");
    }
}
