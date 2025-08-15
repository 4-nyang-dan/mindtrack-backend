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
    private final RedisTemplate<String, byte[]> redisBytesTemplate;
    private final SimilarityCheckService similarityCheckService;

    private static final double threshold = 0.97;

    private String keyThumb(Long userId, long hash){
        return "user:" + userId + ":thumb:" + hash;
    }

    private String keyRecentList(Long userId){
        return "user:" + userId + ":recentImageHashes";
    }

    // 썸네일까지 함께 저장하는 오버로드
    public void cacheRecentImageHash(Long userId, Long imageId, long hash, byte[] pngThumbBytes) {
        cacheRecentImageHashInternal(userId, imageId, hash, Optional.ofNullable(pngThumbBytes));
    }

    // 기존 시그니처도 유지
    public void cacheRecentImageHash(Long userId, Long imageId, long hash) {
        cacheRecentImageHashInternal(userId, imageId, hash, Optional.empty());
    }

    private void cacheRecentImageHashInternal(
            Long userId, Long imageId, long hash, Optional<byte[]> thumbOpt
    ) {
        final String listKey = keyRecentList(userId);
        final String newEntry = imageId + ":" + hash;

        // 1) 동일 해시 제거(중복 방지)
        List<String> cached = redisTemplate.opsForList().range(listKey, 0, -1);
        for (String e : cached) {
            String[] p = e.split(":");
            if (p.length == 2 && p[1].equals(String.valueOf(hash))) {
                redisTemplate.opsForList().remove(listKey, 0, e);
                break;
            }
        }

        // 2) 맨 앞에 추가
        redisTemplate.opsForList().leftPush(listKey, newEntry);

        // 2-1) 썸네일 바이트도 함께 저장 (만료 없음)
        thumbOpt.ifPresent(bytes -> cacheImageThumbByHash(userId, hash, bytes));

        // 3) 10개 초과분은 오른쪽에서 pop → 해당 해시의 썸네일도 함께 삭제
        long size = redisTemplate.opsForList().size(listKey);
        while (size > 10) {
            String removed = redisTemplate.opsForList().rightPop(listKey);
            String[] p = removed.split(":");
            if (p.length == 2) {
                long removedHash = Long.parseLong(p[1]);
                deleteImageThumbByHash(userId, removedHash);
            }
            size = redisTemplate.opsForList().size(listKey);
        }
    }


    private void cacheImageThumbByHash(Long userId, long hash, byte[] pngThumbBytes){
        redisBytesTemplate.opsForValue().set(
                keyThumb(userId, hash),
                pngThumbBytes
        );
    }

    public Optional<byte[]> getImageThumbByHash(Long userId, long hash){
        return Optional.of(redisBytesTemplate.opsForValue().get(keyThumb(userId, hash)));
    }

    public void deleteImageThumbByHash(Long userId, long hash){
        redisBytesTemplate.delete(keyThumb(userId, hash));
    }

    public Optional<Candidate> findMostSimilarFromCache(Long userId, long newHash, int maxDistance){
        String key = keyRecentList(userId);
        List<String> cached = redisTemplate.opsForList().range(key, 0, -1);
        if (cached.isEmpty()) return Optional.empty();

        Candidate best = null;
        double maxSimilarity = 0;

        log.info("---------------------------------------------------------");

        for(String entry : cached){
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;

            Long imageId = Long.parseLong(parts[0]);
            long cachedHash = Long.parseLong(parts[1]);

            int dist = similarityCheckService.hammingDistance(newHash, cachedHash);
            double similarity = similarityCheckService.similarity(newHash, cachedHash);
            log.info("[해밍 거리, 유사도 비교] newHash={} vs cachedHash={} → dist={}, similarity={}, imageId={}",
                    newHash, cachedHash, dist, similarity, imageId);

            if((dist <= maxDistance)){
                if(similarity > maxSimilarity && similarity >= threshold){
                    maxSimilarity = similarity;
                    best = new Candidate(imageId, cachedHash);
                }
            }

            if (best != null) {
                log.info("[최종 선택된 유사 이미지] imageId={}, 해밍 거리={}, 유사도={}", best.imageId, dist, maxSimilarity);
            } else {
                log.info("[유사 이미지 없음] 기준 거리 maxDistance={} 이하 항목 없음", maxDistance);
            }
        }

        return Optional.ofNullable(best);
    }

    public record Candidate(Long imageId, long hash) {}

}
