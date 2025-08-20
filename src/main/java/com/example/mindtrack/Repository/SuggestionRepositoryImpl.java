package com.example.mindtrack.Repository;
import com.example.mindtrack.DTO.Suggestion;
import com.example.mindtrack.DTO.SuggestionPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SuggestionRepositoryImpl implements SuggestionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();

    public SuggestionRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SuggestionPayload> findSuggestionPayloadById(Long id) {
        var base = jdbc.queryForObject(
                "SELECT user_id, created_at FROM suggestions WHERE id = ?",
                (rs, rowNum) -> Map.of(
                        "userId", rs.getString("user_id"),
                        "createdAt", rs.getTimestamp("created_at").toInstant().toString()
                ),
                id
        );

        var items = findSuggestionsJsonByPayloadId(id).stream()
                .flatMap(Optional::stream)
                .map(json -> {
                    try {
                        return om.readValue(json, Suggestion.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        return Optional.of(new SuggestionPayload(id, base.get("userId"), base.get("createdAt"), items));
    }

    @Override
    public Optional<SuggestionPayload> findLatestSuggestionPayloadByUser(String userId) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM suggestions WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                userId
        );

        if (ids.isEmpty()) return Optional.empty();

        return findSuggestionPayloadById(ids.get(0));
    }

    @Override
    public List<Optional<String>> findSuggestionsJsonByPayloadId(Long payloadId) {
        var list = jdbc.queryForList(
                "SELECT row_to_json(i)::text FROM suggestion_items i WHERE i.suggestion_id = ?",
                String.class,
                payloadId
        );
        return list.stream().map(Optional::ofNullable).toList();
    }
}
