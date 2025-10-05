package com.example.mindtrack.Domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Suggestion 하위의 개별 질문(예측 질의)
 * - Suggestion과 다대일 관계
 */
@Getter
@Setter
@Entity
@Table(name = "suggestion_items")
public class SuggestionItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggestion_id", nullable = false)
    private Suggestion suggestion;

    // 질문 내용
    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    /*// AI 답변 (초기 null, 추후 갱신 가능)
    @Column(columnDefinition = "TEXT")
    private String answer;*/
}
