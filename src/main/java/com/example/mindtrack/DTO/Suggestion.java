package com.example.mindtrack.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Suggestion(
        String id, // UUID는 자동으로 생성되므로 이 값은 받아올 필요 없을 수 있음.
        String question, // 질문 내용
        String answer, // AI가 생성한 답변
        Double confidence, // 신뢰도 (nullable)
        Integer rank // 질문 순위 (1, 2, 3 등의 순위)
) {
}
