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
import org.springframework.stereotype.Component;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

@Component
@Slf4j
@RequiredArgsConstructor
public class PgSuggestionsListener implements InitializingBean, DisposableBean {
    private static final String CHANNEL = "suggestions_channel";

    private final DataSource ds;
    private final SuggestionService suggestionService;
    private final SuggestionRepository suggestionRepository;
    private final SuggestionSseHub hub;

    private volatile boolean running = true;
    private Connection conn;
    private PGConnection pg;
    private final ObjectMapper om = new ObjectMapper();

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
                backoffMs = 1000; //성공하면 백오프 리셋
                pollLoop(); // 블로킹-유사 루프 (예외 나면 바깥 catch로)
            } catch (Exception e) {
                log.warn("listen loop error", e);
                sleepQuiet(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 30_000); // 지수 백오프, 최대 30s
            } finally {
                unlistenQuiet();
                closeQuietly(conn);
                conn = null; pg = null;
            }
        }
    }

    /**
     * 커넥션 얻고 LISTEN suggestions_channel실행
     * @throws Exception
     */
    private void connectAndListen() throws Exception {
        conn = ds.getConnection();
        conn.setAutoCommit(true);

        // 네트워크 타임아웃
        conn.setNetworkTimeout(Executors.newSingleThreadExecutor(), 10_000);
        pg = conn.unwrap(PGConnection.class);
        try (Statement st = conn.createStatement()) {
            st.execute("LISTEN " + CHANNEL);
        }
    }

    /**
     * 주기적으로 SELECT 1 실행 -> I/O 처리 유도
     * getNotification()로 알림 목록 조회
     * 각 알림의 JSON 파싱 ->  해당 레코드 suggestionService.findById로 로드
     * hub.publish로 SSE 브로드 캐스트
     * @throws Exception
     */
    private void pollLoop() throws Exception {
        while (running) {
            // 1) 네트워크 I/O를 깨우기 위한 가벼운 쿼리
            try (Statement st = conn.createStatement()) {
                st.execute("SELECT 1");
            }

            // 2) 현재까지 도착한 알림 꺼내기 (논블로킹)
            PGNotification[] notes = pg.getNotifications();
            if (notes != null) {
                for (PGNotification n : notes) {
                    handleNotificationSafe(n);
                }
            }

            // 3) 가벼운 스리프 (너무 타이트하게 돌지 않도록)
            Thread.sleep(300);
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
                log.warn("invalid payload: " + notice.getParameter());
                return;
            }
            suggestionRepository.findSuggestionPayloadById(id).ifPresent(payload -> {
                try {
                    List<Suggestion> suggestions = suggestionService.findSuggestionByPayloadId(payload.id()).stream()
                            .flatMap(Optional::stream) // Optional을 풀고
                            .toList();

                    SuggestionPayload enrichedPayload = new SuggestionPayload(
                            payload.id(),
                            payload.userId(),
                            payload.createdAt(),
                            suggestions // ✅ 여기에 완성된 List<Suggestion> 전달
                    );

                    hub.publish(userId, enrichedPayload, String.valueOf(id));
                }catch(Exception e){
                    //publish 실패는 루프 죽이지 않고 기록만
                    log.warn("publish failed for id=" + id + ", user=" + userId+ ", error=" + e.getMessage());
                }
            });
        } catch (Exception e){
            log.warn("payload parse error: " + notice.getParameter() + ", " + e.getMessage());
        }
    }

    private void unlistenQuiet() {
        if(conn != null) try (Statement st = conn.createStatement()){
            st.execute("UNLISTEN" + CHANNEL);
        }catch (Exception ignore){}
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
    }
}
