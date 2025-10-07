package com.example.mindtrack.Repository;

import com.example.mindtrack.Domain.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    // LazyInitializationException 방지용: items를 함께 로드
    @Query("SELECT s FROM Suggestion s LEFT JOIN FETCH s.items WHERE s.id = :id")
    Optional<Suggestion> findWithItemsById(@Param("id") Long id);
}
