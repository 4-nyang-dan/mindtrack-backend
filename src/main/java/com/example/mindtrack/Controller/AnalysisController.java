package com.example.mindtrack.Controller;

import com.example.mindtrack.DTO.AnalysisResultDto;
import com.example.mindtrack.Service.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import com.example.mindtrack.SSE.SuggestionSseHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/analysis")
public class AnalysisController {

    private final SuggestionSseHub hub; // SSE Hub 주입

    private final SuggestionService suggestionService;

    @PostMapping("/result")
    public ResponseEntity<String> receiveResult(@RequestBody AnalysisResultDto dto) {
        suggestionService.saveFromAnalysisResult(dto);
        return ResponseEntity.ok("결과 수신 완료");
    }
/*    public ResponseEntity<String> receiveResult(@RequestBody Map<String, Object> payload) {
        log.info("📦 받은 분석 결과 전체: {}", payload);

        // AI가 보낸 user_id 추출
        Object userIdObj = payload.get("user_id");
        String userId = (userIdObj != null) ? String.valueOf(userIdObj).trim() : null;

        if (userId == null || userId.isEmpty()) {
            log.warn("⚠️ user_id 누락됨. payload keys={}", payload.keySet());
            return ResponseEntity.badRequest().body("user_id is required");
        }

        try {
            // ✅ 원본 payload 전체를 프론트로 푸시
            hub.publishRaw(userId, payload);
            log.info("✅ SSE publish 성공: userId={}", userId);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("❌ SSE publish 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("SSE publish failed");
        }
    }*/
}

