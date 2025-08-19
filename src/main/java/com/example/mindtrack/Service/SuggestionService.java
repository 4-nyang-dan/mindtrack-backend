package com.example.mindtrack.Service;

import com.example.mindtrack.DTO.Suggestion;
import com.example.mindtrack.DTO.SuggestionPayload;
import com.example.mindtrack.Repository.SuggestionRepository;
import com.example.mindtrack.Util.CustomException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final ObjectMapper om;

    public List<Optional<Suggestion>> findSuggestionByPayloadId(Long payloadId) {
        return suggestionRepository.findSuggestionsJsonByPayloadId(payloadId).stream()
                .map(optJson -> optJson.map(this::toSuggestion))
                .toList();
    }

    private Suggestion toSuggestion(String json){
        try {
            return om.readValue(json, Suggestion.class);
        } catch (Exception e) {
            throw new CustomException("유효하지 않은 suggestion json 입니다.", HttpStatus.BAD_REQUEST);
        }
    }
}

