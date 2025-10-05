package com.example.mindtrack.Service;

import com.example.mindtrack.DTO.AnalysisResultDto;
import com.example.mindtrack.Domain.Suggestion;
import com.example.mindtrack.DTO.SuggestionPayload;
import com.example.mindtrack.Domain.SuggestionItem;
import com.example.mindtrack.Repository.SuggestionRepository;
import com.example.mindtrack.Util.CustomException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final ObjectMapper om;

/*    public List<Optional<Suggestion>> findSuggestionByPayloadId(Long payloadId) {
        return suggestionRepository.findSuggestionsJsonByPayloadId(payloadId).stream()
                .map(optJson -> optJson.map(this::toSuggestion))
                .toList();
    }*/

    @Transactional
    public void saveFromAnalysisResult(AnalysisResultDto dto) {
        // Suggestion 엔티티 생성
        Suggestion suggestion = new Suggestion();
        suggestion.setUserId(dto.getUserId());
        suggestion.setImageId(dto.getImageId());
        suggestion.setDescription(dto.getSuggestion().getDescription());
        suggestion.setPredictedActions(dto.getSuggestion().getPredictedActions());

        // 하위 SuggestionItem 리스트 변환
        List<SuggestionItem> items = dto.getPredictedQuestions().stream()
                .map(q -> {
                    SuggestionItem item = new SuggestionItem();
                    item.setSuggestion(suggestion);
                    item.setQuestion(q.getQuestion());
                    return item;
                })
                .toList();

        suggestion.setItems(items);

        // DB 저장
        suggestionRepository.save(suggestion);

        log.info("AI 분석 결과 저장 완료: userId={}, suggestionId={}",
                dto.getUserId(), suggestion.getId());
    }

    private Suggestion toSuggestion(String json){
        try {
            return om.readValue(json, Suggestion.class);
        } catch (Exception e) {
            throw new CustomException("유효하지 않은 suggestion json 입니다.", HttpStatus.BAD_REQUEST);
        }
    }
}

