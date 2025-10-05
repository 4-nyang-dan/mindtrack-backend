package com.example.mindtrack.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Suggestion 하위의 개별 질문(예측 질의)을 표현하는 DTO
 * - SuggestionItem 엔티티에 대응
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {
    @JsonProperty("question")
    private String question;
}
