package com.example.mindtrack.SSE;

import com.example.mindtrack.DTO.SuggestionPayload;
import com.example.mindtrack.Repository.SuggestionRepository;
import com.example.mindtrack.Service.SuggestionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class PgSuggestionsListener implements InitializingBean, DisposableBean {
    private static final String CHANNEL = "suggestions_channel";

    private final DataSource listenerDs;
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

    // networkTimeout용 단일 스레드
    private final ExecutorService netTimeoutExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pg-listener-netTimeout");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void afterPropertiesSet() {
        Thread t = new Thread(this::runLoop, "pg-listen-suggestions");
        t.setDaemon(true);
        t.start();
    }

    private void runLoop() {
        long backoffMs = 1000;
        while (running) {
            try {
                log.info("🔄 [Listener] Connecting and attaching to channel '{}'", CHANNEL);
                connectAndListen();
                backoffMs = 1000;
                pollLoop();
            } catch (Exception e) {
                log.error("❌ [Listener] listen loop error", e);
                sleepQuiet(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 30_000);
            } finally {
                log.warn("⚠️ [Listener] Cleaning up connection...");
                unlistenQuiet();
                closeQuietly(conn);
                conn = null;
                pg = null;
            }
        }
    }

    private void connectAndListen() throws Exception {
        conn = listenerDs.getConnection();
        conn.setAutoCommit(true);
        conn.setReadOnly(true);

        conn.setNetworkTimeout(netTimeoutExecutor, 10_000);
        pg = conn.unwrap(PGConnection.class);

        try (Statement st = conn.createStatement()) {
            st.execute("LISTEN " + CHANNEL);
        } catch (SQLException e) {
            log.warn("⚠️ [Listener] LISTEN statement failed, reconnecting...", e);
            throw e;
        }

        log.info("✅ [Listener] LISTEN attached on channel '{}'", CHANNEL);
    }

    private void pollLoop() throws Exception {
        log.info("▶️ [Listener] Starting poll loop...");
        while (running) {
            try (Statement st = conn.createStatement()) {
                st.execute("SELECT 1");
            }

            PGNotification[] notes = pg.getNotifications(1000);
            if (notes != null && notes.length > 0) {
                log.info("📩 [Listener] Received {} notifications", notes.length);
                for (PGNotification n : notes) {
                    log.info("📬 [Listener] Raw payload from DB: {}", n.getParameter());
                    handleNotificationSafe(n);
                }
            }
        }
    }

    private void handleNotificationSafe(PGNotification notice) {
        String payload = notice.getParameter();
        log.info("🔔 [Handler] Handling payload: {}", payload);
        try {
            JsonNode node = om.readTree(payload);

            Long id = node.hasNonNull("id") ? node.get("id").asLong() : null;
            String userId = node.hasNonNull("userId") ? node.get("userId").asText() : null;

            log.info("🧩 [Handler] Parsed id={}, userId={}", id, userId);

            if (id == null || userId == null || userId.isBlank()) {
                log.warn("⚠️ [Handler] Invalid payload (missing id/userId): {}", payload);
                return;
            }

            log.info("🔍 [Handler] Fetching suggestion from DB with id={}", id);

            suggestionRepository.findWithItemsById(id).ifPresentOrElse(s -> {
                log.info("✅ [Handler] Found Suggestion id={} for userId={}", s.getId(), s.getUserId());
                SuggestionPayload dto = SuggestionPayload.fromEntity(s);
                log.info("📡 [Handler] Publishing payload to hub for userId={} ...", s.getUserId());
                hub.publish(s.getUserId(), dto, String.valueOf(id));
            }, () -> {
                log.warn("❌ [Handler] No Suggestion found for id={}", id);
            });

        } catch (Exception e) {
            log.error("💥 [Handler] Exception while parsing or handling payload: {}", payload, e);
        }
    }

    private void unlistenQuiet() {
        if (conn != null) {
            try (Statement st = conn.createStatement()) {
                st.execute("UNLISTEN " + CHANNEL);
                log.info("🛑 [Listener] UNLISTEN executed on channel {}", CHANNEL);
            } catch (Exception ignore) {
                log.warn("⚠️ [Listener] Failed to UNLISTEN cleanly");
            }
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c != null) try {
            c.close();
        } catch (Exception ignored) {
        }
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void destroy() {
        log.info("🧹 [Listener] Destroying listener bean...");
        running = false;
        Thread.currentThread().interrupt();
        unlistenQuiet();
        closeQuietly(conn);
        netTimeoutExecutor.shutdownNow();
    }
}
