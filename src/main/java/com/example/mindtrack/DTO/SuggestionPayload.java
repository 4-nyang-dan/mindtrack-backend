package com.example.mindtrack.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SuggestionPayload(
        Long id,
        String userId,          // 디버깅용
        String createdAt,       // ISO-8601 문자열
        List<Suggestion> suggestions
) { }
