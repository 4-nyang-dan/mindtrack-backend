package com.example.mindtrack.Domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "suggestions")
public class Suggestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    private Long imageId; // ScreenshotImage.id 참조

    private String description;

    @ElementCollection
    @CollectionTable(name = "suggestion_actions", joinColumns = @JoinColumn(name = "suggestion_id"))
    @Column(name = "action")
    private List<String> predictedActions;

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "suggestion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SuggestionItem> items;
}
