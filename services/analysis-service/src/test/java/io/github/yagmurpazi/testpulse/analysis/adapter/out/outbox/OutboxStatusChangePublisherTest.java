package io.github.yagmurpazi.testpulse.analysis.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import io.github.yagmurpazi.testpulse.analysis.domain.TestKey;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import io.github.yagmurpazi.testpulse.events.TestStatusChanged;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxStatusChangePublisherTest {

    @Test
    void mapsAStatusChangeToTheEventContract() {
        UUID eventId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        StatusChange change = new StatusChange(UUID.randomUUID(),
                new TestKey("acme/checkout", "io.example.CheckoutTest", "appliesDiscount"),
                TestStatus.SUSPICIOUS, TestStatus.FLAKY, "evidence", Instant.parse("2026-11-26T09:30:00Z"), runId);

        TestStatusChanged event = OutboxStatusChangePublisher.toEvent(change, eventId);

        assertThat(event).isEqualTo(new TestStatusChanged(eventId, TestStatusChanged.SCHEMA_VERSION,
                change.changedAt(), change.testId(), "acme/checkout", "io.example.CheckoutTest", "appliesDiscount",
                io.github.yagmurpazi.testpulse.events.TestStatus.SUSPICIOUS,
                io.github.yagmurpazi.testpulse.events.TestStatus.FLAKY, "evidence", runId));
    }
}
