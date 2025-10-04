package com.example.mindtrack.Repository;

import com.example.mindtrack.DTO.SuggestionPayload;

import java.util.List;
import java.util.Optional;

public interface SuggestionRepository {
    List<Optional<String>> findSuggestionsJsonByPayloadId(Long payloadId);

    Optional<SuggestionPayload> findLatestSuggestionPayloadByUser(String userId);

    Optional<SuggestionPayload> findSuggestionPayloadById(Long id);
}