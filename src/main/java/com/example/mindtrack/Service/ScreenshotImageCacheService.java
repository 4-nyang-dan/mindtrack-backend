package com.example.mindtrack.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScreenshotImageCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SimilarityCheckService similarityCheckService;

    private static final double threshold = 0.97;

    public void cacheRecentImageHash(Long userId, Long imageId, long hash) {
        String key = "user:" + userId + ":recentImageHashes";
        String newEntry = imageId + ":" + hash;

        // 1. 기존 리스트 조회
        List<String> cached = redisTemplate.opsForList().range(key, 0, -1);

        // 2. 동일한 해시가 존재하면 제거
        for (String entry : cached) {
            String[] parts = entry.split(":");
            if (parts.length == 2 && parts[1].equals(String.valueOf(hash))) {
                redisTemplate.opsForList().remove(key, 0, entry); // 모든 방향에서 제거
                break;
            }
        }

        // 3. 맨 앞에 새 항목 추가
        redisTemplate.opsForList().leftPush(key, newEntry);

        // 4. 최대 10개로 유지
        redisTemplate.opsForList().trim(key, 0, 9);
    }


    public Optional<Long> findMostSimilarFromCache(Long userId, long newHash, int maxDistance){
        String key = "user:" + userId + ":recentImageHashes";
        List<String> cached = redisTemplate.opsForList().range(key, 0, -1);

        Long mostSimilarId = null;
        double maxSimilarity = 0;

        log.info("---------------------------------------------------------");

        for(String entry : cached){
            String[] parts = entry.split(":");
            Long imageId = Long.parseLong(parts[0]);
            long cachedHash = Long.parseLong(parts[1]);

            int dist = similarityCheckService.hammingDistance(newHash, cachedHash);
            double similarity = similarityCheckService.similarity(newHash, cachedHash);
            log.info("[해밍 거리, 유사도 비교] newHash={} vs cachedHash={} → dist={}, similarity={}, imageId={}",
                    newHash, cachedHash, dist, similarity, imageId);

            if((dist <= maxDistance)){
                if(similarity > maxSimilarity && similarity >= threshold){
                    maxSimilarity = similarity;
                    mostSimilarId = imageId;
                }
            }

            if (mostSimilarId != null) {
                log.info("[최종 선택된 유사 이미지] imageId={}, 해밍 거리={}, 유사도={}", mostSimilarId, dist, maxSimilarity);
            } else {
                log.info("[유사 이미지 없음] 기준 거리 maxDistance={} 이하 항목 없음", maxDistance);
            }
        }

        return Optional.ofNullable(mostSimilarId);
    }
}
