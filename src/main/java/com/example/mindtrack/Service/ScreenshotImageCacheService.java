package com.example.mindtrack.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScreenshotImageCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, byte[]> redisBytesTemplate;
    private final SimilarityCheckService similarityCheckService;

    // 썸네일/원본/최근목록에 TTL/최대길이 상수 사용
    // 기존에 썸네일만 redis 로 저장하고 있던걸 원본까지 저장하는걸로 수정(fastpai 에서 ocr 및 분석하려면 resize 된 이미지가
    // 아니라 원본 이미지가 필요함)
    private static final Duration TTL_THUMB = Duration.ofHours(1);
    private static final Duration TTL_ORIG = Duration.ofHours(1);
    private static final Duration TTL_RECENT = Duration.ofHours(12);
    private static final int MAX_RECENT = 50;

    private static final double threshold = 0.97;

    private String keyThumb(Long userId, long hash) {
        return "user:" + userId + ":thumb:" + hash;
    }

    private String keyRecentList(Long userId) {
        return "user:" + userId + ":recentImageHashes";
    }

    private String keyOriginal(Long userId, Long imageId) {
        return "user:" + userId + ":img:" + imageId;
    } // 원본 키

    // 썸네일까지 함께 저장하는 오버로드
    public void cacheRecentImageHash(Long userId, Long imageId, long hash, byte[] pngThumbBytes) {
        cacheRecentImageHashInternal(userId, imageId, hash, Optional.ofNullable(pngThumbBytes));
    }

    // 기존 시그니처도 유지
    public void cacheRecentImageHash(Long userId, Long imageId, long hash) {
        cacheRecentImageHashInternal(userId, imageId, hash, Optional.empty());
    }

    // 원본 이미지 바이트 Redis에 TTL로 보관/조회/삭제
    public void cacheOriginalImage(Long userId, Long imageId, byte[] originalBytes) {
        redisBytesTemplate.opsForValue().set(
                keyOriginal(userId, imageId),
                originalBytes,
                TTL_ORIG);
    }

    public Optional<byte[]> getOriginalImage(Long userId, Long imageId) {
        return Optional.ofNullable(redisBytesTemplate.opsForValue().get(keyOriginal(userId, imageId)));
    }

    public void deleteOriginalImage(Long userId, Long imageId) {
        redisBytesTemplate.delete(keyOriginal(userId, imageId));
    }

    private void cacheRecentImageHashInternal(
            Long userId, Long imageId, long hash, Optional<byte[]> thumbOpt) {
        final String listKey = keyRecentList(userId);
        final String newEntry = imageId + ":" + hash;

        // 1) 동일 해시 제거(중복 방지)
        List<String> cached = redisTemplate.opsForList().range(listKey, 0, -1);
        if (cached != null) { // NPE 방지 (range 결과 null일 수 있음!)
            for (String e : cached) {
                String[] p = e.split(":");
                if (p.length == 2 && p[1].equals(String.valueOf(hash))) {
                    redisTemplate.opsForList().remove(listKey, 0, e);
                    break;
                }
            }
        }

        // 2) 맨 앞에 추가
        redisTemplate.opsForList().leftPush(listKey, newEntry);

        // ★ 최근 리스트 자체에도 TTL 적용
        redisTemplate.expire(listKey, TTL_RECENT);

        // 2-1) 썸네일 바이트도 함께 저장 (만료 없음-> TTL 적용으로 수정)
        thumbOpt.ifPresent(bytes -> cacheImageThumbByHash(userId, hash, bytes));

        // 3) 50개 초과분은 오른쪽에서 pop → 해당 해시의 썸네일도 함께 삭제
        // 오토 언박싱(Pop 이나 리스트 비는 문제로 NullPointerException 발생 우려 ) -> 한번 Long 으로 받았다가 null
        // 이면 0 으로 처리하는 방어로 수정
        Long sizeObj = redisTemplate.opsForList().size(listKey);
        long size = (sizeObj == null ? 0 : sizeObj);
        while (size > MAX_RECENT) {
            String removed = redisTemplate.opsForList().rightPop(listKey);
            String[] p = removed.split(":");
            if (p.length == 2) {
                long removedHash = Long.parseLong(p[1]);
                deleteImageThumbByHash(userId, removedHash);
            }
            sizeObj = redisTemplate.opsForList().size(listKey);
            size = (sizeObj == null ? 0 : sizeObj);
        }
    }

    // 썸네일 저장 시 TTL 부여 추가
    private void cacheImageThumbByHash(Long userId, long hash, byte[] pngThumbBytes) {
        redisBytesTemplate.opsForValue().set(
                keyThumb(userId, hash),
                pngThumbBytes,
                TTL_THUMB);
    }

    public Optional<byte[]> getImageThumbByHash(Long userId, long hash) {
        // return Optional.of(redisBytesTemplate.opsForValue().get(keyThumb(userId,
        // hash)));
        return Optional.ofNullable(redisBytesTemplate.opsForValue().get(keyThumb(userId, hash)));
    }

    public void deleteImageThumbByHash(Long userId, long hash) {
        redisBytesTemplate.delete(keyThumb(userId, hash));
    }

    public Optional<Candidate> findMostSimilarFromCache(Long userId, long newHash, int maxDistance) {
        String key = keyRecentList(userId);
        List<String> cached = redisTemplate.opsForList().range(key, 0, -1);
        if (cached == null || cached.isEmpty())
            return Optional.empty();

        Candidate best = null;
        double maxSimilarity = 0;
        int bestDist = Integer.MAX_VALUE;

        for (String entry : cached) {
            String[] parts = entry.split(":");
            if (parts.length != 2)
                continue;

            Long imageId = Long.parseLong(parts[0]);
            long cachedHash = Long.parseLong(parts[1]);

            int dist = similarityCheckService.hammingDistance(newHash, cachedHash);
            double similarity = similarityCheckService.similarity(newHash, cachedHash);

            if (dist <= maxDistance && similarity >= threshold) {
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    best = new Candidate(imageId, cachedHash);
                    bestDist = dist; // ★ dist 기록
                }
            }
        }
        // 루프 밖으로 이동 - 이 전 코드는 루프안에서 출력해서 불필요하게 출력함.
        /*if (best != null) {
            log.info("[최종 선택된 유사 이미지] imageId={}, 해밍 거리={}, 유사도={}", best.imageId, bestDist, maxSimilarity);
        } else {
            log.info("[유사 이미지 없음] 기준 거리 maxDistance={} 이하 항목 없음", maxDistance);
        }
*/
        return Optional.ofNullable(best);
    }

    public record Candidate(Long imageId, long hash) {
    }

}
