package com.example.mindtrack.DTO;

import com.example.mindtrack.Domain.Suggestion;
import com.example.mindtrack.Domain.SuggestionItem;
import java.time.LocalDateTime;
import java.util.List;

public record SuggestionPayload(
        Long id,
        String userId,
        LocalDateTime createdAt,
        List<QuestionDto> questions,
        SuggestionDto suggestion
) {
    public static SuggestionPayload fromEntity(Suggestion s) {
        List<QuestionDto> questionDtos = s.getItems().stream()
                .map(i -> new QuestionDto(i.getQuestion()))
                .toList();

        SuggestionDto suggestionDto = new SuggestionDto(
                s.getRepresentativeImage(),
                s.getDescription(),
                s.getPredictedActions()
        );

        return new SuggestionPayload(
                s.getId(),
                s.getUserId(),
                s.getCreatedAt(),
                questionDtos,
                suggestionDto
        );
    }
}
