package com.example.mindtrack.Controller;

import com.example.mindtrack.DTO.AnalysisResultDto;
import com.example.mindtrack.Service.SuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {

    private final SuggestionService suggestionService;

    @PostMapping("/result")
    public ResponseEntity<String> receiveResult(@RequestBody AnalysisResultDto result) {
        log.info("[AI 결과 수신] userId={}, imageId={}", result.getUserId(), result.getImageId());
        log.info("Suggestion: {}", result.getSuggestion());
        log.info("Predicted Questions: {}", result.getPredictedQuestions());

        // DB 저장 로직
        suggestionService.saveFromAnalysisResult(result);
        log.info(" 분석 결과 DB 저장 완료");

        return ResponseEntity.ok("결과 수신 완료");
    }
}