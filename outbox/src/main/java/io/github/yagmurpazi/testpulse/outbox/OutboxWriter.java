package io.github.yagmurpazi.testpulse.outbox;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores an event in the outbox table. Must be called inside the transaction that changes the data the event
 * describes: the event is then stored if and only if that data is.
 */
public final class OutboxWriter {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    public OutboxWriter(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    /**
     * Appends an event for later publication.
     *
     * @param eventId   unique event id; becomes the outbox row id and the {@code eventId} Kafka header
     * @param topic     destination topic
     * @param key       Kafka record key; events with the same key keep their order
     * @param event     payload, serialized as JSON
     * @param createdAt event time; the relay publishes in this order
     */
    public void append(UUID eventId, String topic, String key, Object event, Instant createdAt) {
        jdbc.sql("""
                        INSERT INTO outbox_event (id, topic, message_key, payload, created_at)
                        VALUES (:id, :topic, :key, :payload, :createdAt)
                        """)
                .param("id", Objects.requireNonNull(eventId, "eventId"))
                .param("topic", Objects.requireNonNull(topic, "topic"))
                .param("key", Objects.requireNonNull(key, "key"))
                .param("payload", jsonMapper.writeValueAsString(Objects.requireNonNull(event, "event")))
                .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .update();
    }
}
