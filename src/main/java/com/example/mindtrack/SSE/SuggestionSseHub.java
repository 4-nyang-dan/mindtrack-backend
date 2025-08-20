package com.example.mindtrack.SSE;

import com.example.mindtrack.DTO.SuggestionPayload;
import com.example.mindtrack.Util.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class SuggestionSseHub {
    private final ConcurrentMap<String, CopyOnWriteArrayList<SseEmitter>> emitters =  new ConcurrentHashMap<>();
    private static final long NO_TIMEOUT = 0L;

    public SseEmitter subscribe(String userId) throws IOException {
        var em = new SseEmitter(NO_TIMEOUT);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(em);

        Runnable cleanup = () -> {
            var list = emitters.get(userId);
            if(list != null){
                list.remove(em);
                if(list.isEmpty()) emitters.remove(userId);
            }
        };
        em.onCompletion(cleanup);
        em.onTimeout(cleanup);
        em.onError(_e -> cleanup.run());

        try {
            em.send(SseEmitter.event().name("heartbeat").data(Map.of("ts", System.currentTimeMillis())).reconnectTime(3000));
        } catch (Exception e) {
            throw new CustomException(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return em;
    }

    public void publish(String userId, SuggestionPayload payload, @Nullable String eventId){
        var list = emitters.getOrDefault(userId, new CopyOnWriteArrayList<>());
        for(var em : list){
            try{
                var ev = SseEmitter.event().name("suggestions").data(payload).reconnectTime(3000);
                if(eventId != null) ev = ev.id(eventId);
                em.send(ev);
            }catch (Exception e) {
                em.complete();
                list.remove(em);
                log.warn("ssehub publish error: " + e.getMessage());
            }
        }
    }
}
