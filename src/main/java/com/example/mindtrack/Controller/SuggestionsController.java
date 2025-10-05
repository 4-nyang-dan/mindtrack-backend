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

/*    @GetMapping("/latest")
    public ResponseEntity<SuggestionPayload> latest(Authentication authentication) {
        String userId = (String) authentication.getPrincipal(); // principal은 userId 문자열
        log.info("suggestion latest 로그: Authentication 유저아이디 ={}", userId);

        Users user = userRepository.findByUserId(userId).orElseThrow();
        String userIdStr = String.valueOf(user.getId());

        log.info("suggestion latest 로그: Authentication 유저 bigint -> text 아이디 ={}", userIdStr);

        return ResponseEntity.of(suggestionRepository.findLatestSuggestionPayloadByUser(userIdStr));
    }*/

    @GetMapping(value="/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam("token") String token,
            @RequestHeader(value="Last-Event-ID", required = false) String lastEventId
    ) {
        log.info("token: {}", token);
        String userId = jwtUtil.extractUserId(token);

        Users user = userRepository.findByUserId(userId).orElseThrow();
        String userIdStr = String.valueOf(user.getId());

        log.info("suggestion stream 로그: 토큰에서 추출한 아이디(숫자)={}", userIdStr);

        return hub.subscribe(userIdStr);
    }
}
