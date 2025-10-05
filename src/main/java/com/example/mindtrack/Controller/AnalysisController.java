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

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/analysis")
public class AnalysisController {

    private final SuggestionService suggestionService;

    @PostMapping("/result")
    public ResponseEntity<String> receiveResult(@RequestBody AnalysisResultDto dto) {
        suggestionService.saveFromAnalysisResult(dto);
        return ResponseEntity.ok("결과 수신 완료");
    }
}

