package com.example.mindtrack.Controller;

import com.example.mindtrack.Config.JWT.JwtUtil;
import com.example.mindtrack.DTO.SuggestionPayload;
import com.example.mindtrack.Domain.Users;
import com.example.mindtrack.Repository.SuggestionRepository;
import com.example.mindtrack.Repository.UserRepository;
import com.example.mindtrack.SSE.SuggestionSseHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/suggestions")
public class SuggestionsController {
    private final SuggestionSseHub hub;
    private final SuggestionRepository suggestionRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @GetMapping("/latest")
    public ResponseEntity<?> latest(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        log.info("suggestion latest 요청 userId={}", userId);

        Users user = userRepository.findByUserId(userId).orElseThrow();
        String userIdStr = String.valueOf(user.getId());

        // 1️⃣ 먼저 SuggestionRepository 에서 최신 payload 확인
        var suggestionOpt = suggestionRepository.findLatestSuggestionPayloadByUser(userIdStr);
        if (suggestionOpt.isPresent()) {
            log.info("suggestion 테이블 기반 데이터 반환");
            return ResponseEntity.ok(suggestionOpt.get());
        }

        // 2️⃣ 없으면 ScreenshotImage 의 analysisResult 기반으로 대체
        var screenshotOpt = screenshotImageRepository.findTopByUser_IdOrderByCapturedAtDesc(user.getId());
        if (screenshotOpt.isPresent() && screenshotOpt.get().getAnalysisResult() != null) {
            try {
                ObjectMapper om = new ObjectMapper();
                Map<String, Object> resultMap = om.readValue(screenshotOpt.get().getAnalysisResult(), Map.class);
                log.info("analysis_result 기반 데이터 반환");
                return ResponseEntity.ok(resultMap);
            } catch (Exception e) {
                log.error("analysis_result JSON 파싱 오류", e);
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "invalid analysis_result JSON"));
            }
        }

        return ResponseEntity.noContent().build();
    }


    @GetMapping(value="/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam("token") String token,
            @RequestHeader(value="Last-Event-ID", required = false) String lastEventId
    ) throws IOException {
        log.info("token: {}", token);
        String userId = jwtUtil.extractUserId(token);

        Users user = userRepository.findByUserId(userId).orElseThrow();
        String userIdStr = String.valueOf(user.getId());

        log.info("suggestion stream 로그: 토큰에서 추출한 아이디(숫자)={}", userIdStr);

        return hub.subscribe(userIdStr);
    }
}
