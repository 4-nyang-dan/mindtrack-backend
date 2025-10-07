package com.example.mindtrack.SSE;

import com.example.mindtrack.DTO.SuggestionPayload;
import com.example.mindtrack.Util.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class SuggestionSseHub {
    // 사용자별 연결된 SSE Emitter 세션 목록
    private final ConcurrentMap<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    // SSE 기본 timeout: 0은 무제한
    private static final long NO_TIMEOUT = 0L;

    /**
     * 클라이언트가 구독 요청을 보낼 때 호출
     * @param userId SSE 연결을 식별할 사용자 ID
     * @return SseEmitter객체
     */
    public SseEmitter subscribe(Long userId) {
        SseEmitter em = new SseEmitter(NO_TIMEOUT);
        emitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(em);

        // Emitter 정리 로직: 연결 종료, 타임아웃, 에러 발생 시 호출됨
        Runnable cleanup = () -> {
            Set<SseEmitter> list = emitters.get(userId);
            if(list != null){
                list.remove(em);
                if(list.isEmpty()) emitters.remove(userId);
            }
        };
        em.onCompletion(cleanup);
        em.onTimeout(cleanup);
        em.onError(ex -> cleanup.run());

        // 초기 heartbeat 이벤트 전송(연결 테스트용)
        try {
            em.send(SseEmitter.event()
                    .name("heartbeat")
                    .data(Map.of("ts", System.currentTimeMillis()))
                    .reconnectTime(3000));
        } catch (IOException e) {
            em.complete();
            cleanup.run();
            log.debug("초기 heartbeat 전송 실패: {}", e.getMessage());
        }

        return em;
    }

    /**
     * 특정 사용자에게 알림 이벤트를 발행
     * @param userId 이벤트를 받을 사용자 ID
     * @param payload 전달할 데이터
     * @param eventId 이벤트 고유 ID(Last-Event-ID 복구용)
     */
    public void publish(Long userId, SuggestionPayload payload, @Nullable String eventId){
        Set<SseEmitter> list = emitters.get(userId);
        if(list == null || list.isEmpty()) {
            log.info("⚠️ No SSE subscribers found for userId={}", userId);
            return;
        }

        for(SseEmitter emitter : list){
            try{
                var event = SseEmitter.event()
                        .name("suggestions")
                        .data(payload)
                        .reconnectTime(3000);

                if(eventId != null) event = event.id(eventId);

                emitter.send(event);
                log.info("✅ Emitter send complete for userId={}", userId);
            }catch (IOException e) {
                log.info("Publish failed for user {} : {}", userId, e.getMessage());
                emitter.completeWithError(e);
                list.remove(emitter);
            }
        }
    }

    /**
     * 모든 연결된 클라이언트에게 주기적으로 heartbeat 이벤트를 전송
     * DB 이벤트가 오랫동안 없더라도 연결이 끊기지 않도록 유지
     */
    @Scheduled(fixedRate = 25000)
    public void broadcastHeartbeat(){
        long ts = System.currentTimeMillis();
        emitters.forEach((userId, list) -> {
            for(SseEmitter emitter : list){
                try{
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(Map.of("ts", ts))
                            .reconnectTime(3000));
                } catch(IOException e){
                    emitter.complete();
                    list.remove(emitter);
                    log.debug("Heartbeat failed for user {} : {}", userId, e.getMessage());
                }
            }
        });
    }

/*    public void publishRaw(String userId, Map<String, Object> rawPayload) {
    var list = emitters.getOrDefault(userId, new CopyOnWriteArrayList<>());
    for (var em : list) {
        try {
            var ev = SseEmitter.event()
                    .name("suggestions")
                    .data(rawPayload)
                    .reconnectTime(3000);
            em.send(ev);
        } catch (Exception e) {
            em.complete();
            list.remove(em);
            log.warn("ssehub publish error: " + e.getMessage());
        }
    }*/
}
