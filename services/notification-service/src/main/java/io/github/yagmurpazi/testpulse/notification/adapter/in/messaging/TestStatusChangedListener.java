package io.github.yagmurpazi.testpulse.notification.adapter.in.messaging;

import io.github.yagmurpazi.testpulse.events.TestStatusChanged;
import io.github.yagmurpazi.testpulse.events.Topics;
import io.github.yagmurpazi.testpulse.notification.application.port.in.NotificationResult;
import io.github.yagmurpazi.testpulse.notification.application.port.in.NotifyStatusChangeUseCase;
import io.github.yagmurpazi.testpulse.notification.domain.StatusChange;
import io.github.yagmurpazi.testpulse.notification.domain.TestStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes {@code TestStatusChanged} events. The offset is committed after {@link #onTestStatusChanged} returns;
 * a redelivered event finds its deliveries recorded and sends nothing twice.
 */
@Component
class TestStatusChangedListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestStatusChangedListener.class);

    private final NotifyStatusChangeUseCase notifyStatusChange;
    private final JsonMapper jsonMapper;

    TestStatusChangedListener(NotifyStatusChangeUseCase notifyStatusChange, JsonMapper jsonMapper) {
        this.notifyStatusChange = notifyStatusChange;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = Topics.TEST_STATUS_CHANGES)
    void onTestStatusChanged(ConsumerRecord<String, String> record) {
        StatusChange change = toStatusChange(parse(record.value()));
        NotificationResult result = notifyStatusChange.notify(change);
        if (result.outcome() == NotificationResult.Outcome.NOTIFIED) {
            LOG.info("Notified {} -> {} of {} via {}{}", change.from(), change.to(), change.testLabel(),
                    result.delivered(), result.alreadyDelivered().isEmpty() ? ""
                            : " (already delivered via " + result.alreadyDelivered() + ")");
        } else {
            LOG.debug("Did not notify {} -> {} of {}: {}", change.from(), change.to(), change.testLabel(),
                    result.outcome());
        }
    }

    TestStatusChanged parse(String json) {
        TestStatusChanged event;
        try {
            event = jsonMapper.readValue(json, TestStatusChanged.class);
        } catch (JacksonException e) {
            // includes violations of the event's own validation, which Jackson wraps
            throw new InvalidEventException("Unreadable TestStatusChanged event: " + e.getMessage(), e);
        }
        if (event == null) {
            throw new InvalidEventException("Empty TestStatusChanged event");
        }
        if (event.schemaVersion() != TestStatusChanged.SCHEMA_VERSION) {
            throw new InvalidEventException("Unsupported TestStatusChanged schema version " + event.schemaVersion());
        }
        return event;
    }

    static StatusChange toStatusChange(TestStatusChanged event) {
        return new StatusChange(event.eventId(), event.testId(), event.repository(), event.className(),
                event.testName(), TestStatus.valueOf(event.previousStatus().name()),
                TestStatus.valueOf(event.newStatus().name()), event.reason(), event.occurredAt(),
                event.triggeringRunId() == null);
    }
}
