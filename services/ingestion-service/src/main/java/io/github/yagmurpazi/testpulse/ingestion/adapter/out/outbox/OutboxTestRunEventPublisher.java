package io.github.yagmurpazi.testpulse.ingestion.adapter.out.outbox;

import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import io.github.yagmurpazi.testpulse.events.Topics;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunEventPublisher;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes by writing to the outbox table. Runs inside the caller's transaction, so the event is stored if and
 * only if the run is stored. Delivery to Kafka happens later, in the outbox relay.
 */
@Component
class OutboxTestRunEventPublisher implements TestRunEventPublisher {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    OutboxTestRunEventPublisher(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void publishIngested(TestRun run) {
        TestRunIngested event = TestRunIngestedMapper.toEvent(run, UUID.randomUUID());
        jdbc.sql("""
                        INSERT INTO outbox_event (id, topic, message_key, payload, created_at)
                        VALUES (:id, :topic, :key, :payload, :createdAt)
                        """)
                .param("id", event.eventId())
                .param("topic", Topics.TEST_RUNS)
                .param("key", event.repository())
                .param("payload", jsonMapper.writeValueAsString(event))
                .param("createdAt", OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                .update();
    }
}
