package io.github.yagmurpazi.testpulse.notification.adapter.out.persistence;

import io.github.yagmurpazi.testpulse.notification.application.port.out.DeliveryLog;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Delivery records in PostgreSQL. Every call is its own transaction: one delivery, one row. */
@Repository
class JdbcDeliveryLog implements DeliveryLog {

    private final JdbcClient jdbc;

    JdbcDeliveryLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isDelivered(UUID eventId, String channel) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM delivered_notification WHERE event_id = :eventId "
                        + "AND channel = :channel)")
                .param("eventId", eventId)
                .param("channel", channel)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void recordDelivered(UUID eventId, String channel, Instant deliveredAt) {
        jdbc.sql("INSERT INTO delivered_notification (event_id, channel, delivered_at) "
                        + "VALUES (:eventId, :channel, :deliveredAt) ON CONFLICT DO NOTHING")
                .param("eventId", eventId)
                .param("channel", channel)
                .param("deliveredAt", OffsetDateTime.ofInstant(deliveredAt, ZoneOffset.UTC))
                .update();
    }
}
