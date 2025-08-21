package com.example.mindtrack.SSE;

import com.example.mindtrack.DTO.Suggestion;
import com.example.mindtrack.DTO.SuggestionPayload;
import com.example.mindtrack.Repository.SuggestionRepository;
import com.example.mindtrack.Service.SuggestionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class PgSuggestionsListener implements InitializingBean, DisposableBean {
    private static final String CHANNEL = "suggestions_channel";

    // 전용 풀 주입
    private final DataSource listenerDs;

    // 서비스/리포지토리는 기존 메인 풀을 사용(설정 변경 불필요)
    private final SuggestionService suggestionService;
    private final SuggestionRepository suggestionRepository;
    private final SuggestionSseHub hub;

    private volatile boolean running = true;
    private Connection conn;
    private PGConnection pg;
    private final ObjectMapper om = new ObjectMapper();

    @Autowired
    public PgSuggestionsListener(
            @Qualifier("listenerDataSource") DataSource listenerDs,
            SuggestionService suggestionService,
            SuggestionRepository suggestionRepository,
            SuggestionSseHub hub
    ) {
        this.listenerDs = listenerDs;
        this.suggestionService = suggestionService;
        this.suggestionRepository = suggestionRepository;
        this.hub = hub;
    }

    // networkTimeout용 단일 스레드(선택)
    private final ExecutorService netTimeoutExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pg-listener-netTimeout");
        t.setDaemon(true);
        return t;
    });

    @Override public void afterPropertiesSet() {
        Thread t = new Thread(this::runLoop, "pg-listen-suggestions");
        t.setDaemon(true);
        t.start();
    }

    private void runLoop() {
        long backoffMs = 1000;
        while (running) {
            try {
                connectAndListen();
                backoffMs = 1000;
                pollLoop();
            } catch (Exception e) {
                log.warn("listen loop error", e);
                sleepQuiet(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 30_000);
            } finally {
                unlistenQuiet();
                closeQuietly(conn);
                conn = null; pg = null;
            }
        }
    }

    private void connectAndListen() throws Exception {
        conn = listenerDs.getConnection();
        conn.setAutoCommit(true);
        conn.setReadOnly(true); // 🟩 리스너 커넥션은 읽기 전용

        // (선택) 네트워크 타임아웃: Executor 누수 방지 위해 필드 executor 사용
        conn.setNetworkTimeout(netTimeoutExecutor, 10_000);

        pg = conn.unwrap(PGConnection.class);
        try (Statement st = conn.createStatement()) {
            st.execute("LISTEN " + CHANNEL);
        }
        log.info("LISTEN attached on channel {}", CHANNEL);
    }

    private void pollLoop() throws Exception {
        while (running) {
            // 1) I/O를 깨우는 가벼운 쿼리
            try (Statement st = conn.createStatement()) {
                st.execute("SELECT 1");
            }

            // 2) 알림 처리
            PGNotification[] notes = pg.getNotifications();
            if (notes != null) {
                for (PGNotification n : notes) {
                    handleNotificationSafe(n);
                }
            }

            // 3) 과도 루프 방지
            Thread.sleep(700); // 0.7s (환경에 맞게 300~1000ms 조정)
        }
    }

    private void handleNotificationSafe(PGNotification notice){
        try{
            JsonNode node = om.readTree(notice.getParameter());
            Long id = node.hasNonNull("id")
                    ? (node.get("id").isNumber()
                    ? node.get("id").asLong()
                    : Long.parseLong(node.get("id").asText()))
                    : null;
            String userId = node.hasNonNull("userId") ? node.get("userId").asText() : null;
            if(id == null || userId == null || userId.isBlank()){
                log.warn("invalid payload: {}", notice.getParameter());
                return;
            }

            //아래 조회/가공은 메인 풀(DataSource) 사용하는 Service/Repository에서 수행됨
            suggestionRepository.findSuggestionPayloadById(id).ifPresent(payload -> {
                try {
                    List<Suggestion> suggestions = suggestionService.findSuggestionByPayloadId(payload.id()).stream()
                            .flatMap(Optional::stream)
                            .toList();

                    SuggestionPayload enrichedPayload = new SuggestionPayload(
                            payload.id(),
                            payload.userId(),
                            payload.createdAt(),
                            suggestions
                    );
                    hub.publish(userId, enrichedPayload, String.valueOf(id));
                }catch(Exception e){
                    log.warn("publish failed for id={}, user={}, error={}", id, userId, e.getMessage());
                }
            });
        } catch (Exception e){
            log.warn("payload parse error: {}, {}", notice.getParameter(), e.getMessage());
        }
    }

    private void unlistenQuiet() {
        if(conn != null) try (Statement st = conn.createStatement()){
            st.execute("UNLISTEN " + CHANNEL); // 🟩 공백 추가
        } catch (Exception ignore) {}
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c != null) try { c.close(); } catch (Exception ignored) {}
    }

    private static void sleepQuiet(long ms){
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    @Override public void destroy() {
        running = false;
        unlistenQuiet();
        closeQuietly(conn);
        // executor 정리
        netTimeoutExecutor.shutdownNow();
    }
}
