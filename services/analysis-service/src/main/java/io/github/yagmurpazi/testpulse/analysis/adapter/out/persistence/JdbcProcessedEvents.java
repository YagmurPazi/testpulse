package io.github.yagmurpazi.testpulse.analysis.adapter.out.persistence;

import io.github.yagmurpazi.testpulse.analysis.application.port.out.ProcessedEvents;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Processed event ids. Inserted in the processing transaction: if processing fails, the marker is rolled back too
 * and the redelivered event is processed again.
 */
@Repository
class JdbcProcessedEvents implements ProcessedEvents {

    private final JdbcClient jdbc;

    JdbcProcessedEvents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markProcessed(UUID eventId) {
        return jdbc.sql("INSERT INTO processed_event (event_id) VALUES (:eventId) ON CONFLICT DO NOTHING")
                .param("eventId", eventId)
                .update() == 1;
    }
}
