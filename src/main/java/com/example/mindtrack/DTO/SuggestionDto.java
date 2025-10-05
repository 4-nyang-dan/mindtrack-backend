package com.example.mindtrack.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 * AI의 제안(Suggestion) 단위
 * - 대표 이미지, 설명, 예측된 행동 리스트 포함
 */
@Getter
@Setter
public class SuggestionDto {

    @JsonProperty("representative_image")
    private String representativeImage; // 대표 이미지 경로

    private String description; // AI 요약 설명

    @JsonProperty("predicted_actions")
    private List<String> predictedActions; // 행동 제안 리스트

    public SuggestionDto(String representativeImage, String description, List<String> predictedActions) {
        this.representativeImage = representativeImage;
        this.description = description;
        this.predictedActions = predictedActions;
    }
}
