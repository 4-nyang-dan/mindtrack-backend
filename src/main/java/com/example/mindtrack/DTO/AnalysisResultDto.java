package com.example.mindtrack.DTO;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class AnalysisResultDto {
    private String userId;
    private Long imageId;
    private SuggestionDto suggestion;
    private List<QuestionDto> predictedQuestions;
}