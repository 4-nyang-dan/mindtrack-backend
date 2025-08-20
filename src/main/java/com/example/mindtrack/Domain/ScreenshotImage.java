package com.example.mindtrack.Domain;

import com.example.mindtrack.Enum.AnalysisStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class ScreenshotImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id") // FK 컬럼명 → users.id(Long) 참조
    private Users user;

    private Long imageHash;

    private int visitCnt;

    private LocalDateTime capturedAt;

    private LocalDateTime lastVisitedAt;

    @Column(columnDefinition = "TEXT")
    private String analysisResult; // 결과 요약 or Json 저장

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus analysisStatus;

    public void updateLastVisited(LocalDateTime lastVisitedAt) {
        this.lastVisitedAt = lastVisitedAt;
        this.visitCnt++;
    }

    public ScreenshotImage updateResult(String analysisResult, AnalysisStatus analysisStatus) {
        this.analysisResult = analysisResult;
        this.analysisStatus = analysisStatus;

        return this;
    }

    @Builder
    public ScreenshotImage(Users user, Long imageHash, int visitCnt, LocalDateTime capturedAt,
            LocalDateTime lastVisitedAt, AnalysisStatus analysisStatus) {
        this.user = user;
        this.imageHash = imageHash;
        this.visitCnt = visitCnt;
        this.capturedAt = capturedAt;
        this.lastVisitedAt = lastVisitedAt;
        this.analysisStatus = analysisStatus;
    }

}
