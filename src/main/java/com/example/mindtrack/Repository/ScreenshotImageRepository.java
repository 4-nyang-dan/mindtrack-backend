package com.example.mindtrack.Repository;

import com.example.mindtrack.Domain.ScreenshotImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScreenshotImageRepository extends JpaRepository<ScreenshotImage,Long> {

    // 최신 저장된 이미지 가져오기
    Optional<ScreenshotImage> findTopByUser_IdOrderByCapturedAtDesc(Long userId);

    Optional<ScreenshotImage> findTopByUser_IdOrderByLastVisitedAtDesc(Long userId);

    // 동일 해시 이미지
    Optional<ScreenshotImage> findTopByUser_IdAndId(Long userId, Long imageId);
}
