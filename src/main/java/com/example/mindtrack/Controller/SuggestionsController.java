package com.example.mindtrack.Controller;

import com.example.mindtrack.Config.JWT.JwtUtil;
import com.example.mindtrack.DTO.SuggestionPayload;
import com.example.mindtrack.Repository.SuggestionRepository;
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
    private final JwtUtil jwtUtil;

    @GetMapping("/latest")
    public ResponseEntity<SuggestionPayload> latest(Authentication authentication) {
        String userId = (String) authentication.getPrincipal(); // principal은 userId 문자열
        return ResponseEntity.of(suggestionRepository.findLatestSuggestionPayloadByUser(userId));
    }

    @GetMapping(value="/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam("token") String token,
            @RequestHeader(value="Last-Event-ID", required = false) String lastEventId
    ) throws IOException {
        log.info("token: {}", token);
        String userId = jwtUtil.extractUserId(token); // ✅ JWT에서 userId 파싱
        return hub.subscribe(userId);
    }
}
