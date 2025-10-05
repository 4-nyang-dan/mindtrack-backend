package com.example.mindtrack.Domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 분석 결과(제안) 엔티티
 * - 하나의 분석 결과를 대표하는 상위 엔티티
 * - 여러 개의 SuggestionItem(질문)을 가짐
 */
@Getter
@Setter
@Entity
@Table(name = "suggestions")
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 분석 요청자
    @Column(nullable = false)
    private String userId;

    // 연결된 스크린샷 이미지 ID
    private Long imageId;

    // 대표 이미지 경로 (AI가 제공한 썸네일)
    private String representativeImage;

    // 분석 요약 설명
    @Column(columnDefinition = "TEXT")
    private String description;

    // AI가 예측한 행동 목록
    @ElementCollection
    @CollectionTable(name = "suggestion_actions", joinColumns = @JoinColumn(name = "suggestion_id"))
    @Column(name = "action")
    private List<String> predictedActions;

    // 자동 생성 시간
    @CreationTimestamp
    private LocalDateTime createdAt;

    // 하위 질문 목록
    @OneToMany(mappedBy = "suggestion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SuggestionItem> items;
}
