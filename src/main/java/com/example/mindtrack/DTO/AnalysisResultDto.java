package com.example.mindtrack.DTO;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
public class AnalysisResultDto {
    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("image_id") // payload 에서 보낸 필드명과 일치 시킨다.
    private Long imageId;

    private SuggestionDto suggestion;

    @JsonProperty("predicted_questions")
    private List<QuestionDto> predictedQuestions;
}