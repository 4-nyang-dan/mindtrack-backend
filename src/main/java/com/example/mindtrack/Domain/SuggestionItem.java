package com.example.mindtrack.Domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "suggestion_items")
public class SuggestionItem {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggestion_id")
    private Suggestion suggestion;

    @Column(nullable = false)
    private String question;

    private String answer; // 현재 null 허용 - 클릭 시 AI 호출 후 다시 저장
}
