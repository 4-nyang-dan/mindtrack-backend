package com.example.mindtrack.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SuggestionPayload(
                Long id, // suggestion 테이블의 id (BIGSERIAL)
                String userId, // 사용자의 ID (TEXT)
                String createdAt, // ISO-8601 날짜 형식
                List<Suggestion> suggestions // 각 이미지에 대한 질문과 답변들
) {
}
