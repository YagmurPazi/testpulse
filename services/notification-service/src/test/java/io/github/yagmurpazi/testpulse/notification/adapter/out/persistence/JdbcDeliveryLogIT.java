package io.github.yagmurpazi.testpulse.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.notification.IntegrationTest;
import io.github.yagmurpazi.testpulse.notification.application.port.out.DeliveryLog;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JdbcDeliveryLogIT extends IntegrationTest {

    @Autowired
    private DeliveryLog deliveryLog;

    @Test
    void recordsDeliveriesPerEventAndChannel() {
        UUID eventId = UUID.randomUUID();

        deliveryLog.recordDelivered(eventId, "email", Instant.parse("2026-11-25T12:00:00Z"));

        assertThat(deliveryLog.isDelivered(eventId, "email")).isTrue();
        assertThat(deliveryLog.isDelivered(eventId, "slack")).isFalse();
        assertThat(deliveryLog.isDelivered(UUID.randomUUID(), "email")).isFalse();
    }

    @Test
    void recordingTheSameDeliveryTwiceKeepsTheFirst() {
        UUID eventId = UUID.randomUUID();

        deliveryLog.recordDelivered(eventId, "email", Instant.parse("2026-11-25T12:00:00Z"));
        deliveryLog.recordDelivered(eventId, "email", Instant.parse("2026-11-25T13:00:00Z"));

        assertThat(count("delivered_notification")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT delivered_at FROM delivered_notification").query(Instant.class).single())
                .isEqualTo("2026-11-25T12:00:00Z");
    }
}
