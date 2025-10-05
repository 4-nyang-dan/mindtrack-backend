package com.example.mindtrack.Controller;

import com.example.mindtrack.Config.JWT.JwtUtil;
import com.example.mindtrack.DTO.SuggestionPayload;
import com.example.mindtrack.Domain.Users;
import com.example.mindtrack.Repository.SuggestionRepository;
import com.example.mindtrack.Repository.UserRepository;
import com.example.mindtrack.SSE.SuggestionSseHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
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

    /*
     * @GetMapping("/latest")
     * public ResponseEntity<SuggestionPayload> latest(Authentication
     * authentication) {
     * String userId = (String) authentication.getPrincipal(); // principal은 userId
     * 문자열
     * log.info("suggestion latest 로그: Authentication 유저아이디 ={}", userId);
     * 
     * Users user = userRepository.findByUserId(userId).orElseThrow();
     * String userIdStr = String.valueOf(user.getId());
     * 
     * log.info("suggestion latest 로그: Authentication 유저 bigint -> text 아이디 ={}",
     * userIdStr);
     * 
     * return
     * ResponseEntity.of(suggestionRepository.findLatestSuggestionPayloadByUser(
     * userIdStr));
     * }
     */

    /*
     * cors 문제 원인
     * Electron 에서 자동 실행되면서 서버 측 SecurityContext 를 계속 점유한 상태로 연결 유지
     * 
     * Spring의 ThreadLocal 기반 SecurityContext 가 해당 연결을 계속 붙잡고 있음
     * 이후 들어오는 다른 요청(/api/auth/signup)이 같은 컨텍스트 풀에서 처리될 때,
     * body를 읽지 못하거나 validation 에러를 내버림
     */
    /*
     * @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
     * public SseEmitter stream(
     * 
     * @RequestParam("token") String token,
     * 
     * @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId)
     * {
     * log.info("token: {}", token);
     * String userId = jwtUtil.extractUserId(token);
     * 
     * Users user = userRepository.findByUserId(userId).orElseThrow();
     * String userIdStr = String.valueOf(user.getId());
     * 
     * log.info("suggestion stream 로그: 토큰에서 추출한 아이디(숫자)={}", userIdStr);
     * 
     * return hub.subscribe(userIdStr);
     * }
     */

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam("token") String token,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        log.info("[SSE] 클라이언트 연결 시도 token={}", token);

        // JWT 직접 검증 (Spring Security 인증 X)
        try {
            String userId = jwtUtil.extractUserId(token);
            Users user = userRepository.findByUserId(userId).orElseThrow();
            String userIdStr = String.valueOf(user.getId());
            log.info("[SSE] 구독 완료 userId={}", userIdStr);

            return hub.subscribe(userIdStr);
        } catch (Exception e) {
            log.warn("[SSE] 토큰 검증 실패: {}", e.getMessage());
            // 잘못된 토큰이면 401 반환
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SSE token");
        }
    }
}
