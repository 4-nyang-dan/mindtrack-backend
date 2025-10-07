package com.example.mindtrack.DTO;

import com.example.mindtrack.Domain.Suggestion;

import java.time.LocalDateTime;
import java.util.List;

// 클라이언트(SSE나 REST GET 응답)에 전달할 때 쓰는 전송 전용 
public record SuggestionPayload(
        Long id,
        Long userId,
        //LocalDateTime createdAt,
        List<QuestionDto> predicted_questions,
        SuggestionDto suggestion) {
        public static SuggestionPayload fromEntity(Suggestion s) {
                List<QuestionDto> questionDtos = s.getItems().stream()
                                .map(i -> new QuestionDto(i.getQuestion()))
                                .toList();

                SuggestionDto suggestionDto = new SuggestionDto(
                                s.getRepresentativeImage(),
                                s.getDescription(),
                                s.getPredictedActions());

                return new SuggestionPayload(
                                s.getId(),
                                s.getUserId(),
                                //s.getCreatedAt(),
                                questionDtos,
                                suggestionDto);
        }
}