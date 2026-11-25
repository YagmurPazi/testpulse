package io.github.yagmurpazi.testpulse.analysis.adapter.out.outbox;

import io.github.yagmurpazi.testpulse.analysis.application.port.out.StatusChangePublisher;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import io.github.yagmurpazi.testpulse.events.TestStatus;
import io.github.yagmurpazi.testpulse.events.TestStatusChanged;
import io.github.yagmurpazi.testpulse.events.Topics;
import io.github.yagmurpazi.testpulse.outbox.OutboxWriter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Publishes status changes through the transactional outbox, keyed by repository. */
@Component
class OutboxStatusChangePublisher implements StatusChangePublisher {

    private final OutboxWriter outbox;

    OutboxStatusChangePublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publish(StatusChange change) {
        TestStatusChanged event = toEvent(change, UUID.randomUUID());
        outbox.append(event.eventId(), Topics.TEST_STATUS_CHANGES, event.repository(), event, event.occurredAt());
    }

    static TestStatusChanged toEvent(StatusChange change, UUID eventId) {
        return new TestStatusChanged(
                eventId,
                TestStatusChanged.SCHEMA_VERSION,
                change.changedAt(),
                change.testId(),
                change.key().repository(),
                change.key().className(),
                change.key().testName(),
                TestStatus.valueOf(change.from().name()),
                TestStatus.valueOf(change.to().name()),
                change.reason(),
                change.triggeringRunId());
    }
}
