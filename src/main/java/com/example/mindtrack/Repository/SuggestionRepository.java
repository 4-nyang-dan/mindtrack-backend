package com.example.mindtrack.Repository;

import com.example.mindtrack.Domain.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    // 최신 제안 하나 찾기
    Optional<Suggestion> findTopByUserIdOrderByCreatedAtDesc(String userId);

    // 특정 유저의 모든 제안
    List<Suggestion> findAllByUserIdOrderByCreatedAtDesc(String userId);
}
