package io.github.yagmurpazi.testpulse.notification.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.yagmurpazi.testpulse.events.TestStatus;
import io.github.yagmurpazi.testpulse.events.TestStatusChanged;
import io.github.yagmurpazi.testpulse.notification.application.port.in.NotifyStatusChangeUseCase;
import io.github.yagmurpazi.testpulse.notification.domain.StatusChange;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class TestStatusChangedListenerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    private final TestStatusChangedListener listener =
            new TestStatusChangedListener(mock(NotifyStatusChangeUseCase.class), jsonMapper);

    @Test
    void mapsAnEvaluationToAnAutomaticChange() {
        TestStatusChanged event = TestStatusChangedEvents.event("acme/checkout", "appliesDiscount",
                TestStatus.SUSPICIOUS, TestStatus.FLAKY);

        StatusChange change = TestStatusChangedListener.toStatusChange(listener.parse(
                jsonMapper.writeValueAsString(event)));

        assertThat(change.eventId()).isEqualTo(event.eventId());
        assertThat(change.testId()).isEqualTo(event.testId());
        assertThat(change.repository()).isEqualTo("acme/checkout");
        assertThat(change.className()).isEqualTo("io.example.checkout.CheckoutServiceTest");
        assertThat(change.testName()).isEqualTo("appliesDiscount");
        assertThat(change.from()).isEqualTo(io.github.yagmurpazi.testpulse.notification.domain.TestStatus.SUSPICIOUS);
        assertThat(change.to()).isEqualTo(io.github.yagmurpazi.testpulse.notification.domain.TestStatus.FLAKY);
        assertThat(change.reason()).isEqualTo(event.reason());
        assertThat(change.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(change.manual()).isFalse();
    }

    @Test
    void aChangeWithoutTriggeringRunIsManual() {
        TestStatusChanged event = TestStatusChangedEvents.event("acme/checkout", "appliesDiscount",
                TestStatus.FLAKY, TestStatus.QUARANTINED);

        assertThat(TestStatusChangedListener.toStatusChange(event).manual()).isTrue();
    }

    @Test
    void rejectsUnreadableJsonAsPermanent() {
        assertThatThrownBy(() -> listener.parse("{ not json")).isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectsAnEventThatViolatesTheContract() {
        ObjectNode same = jsonMapper.valueToTree(TestStatusChangedEvents.event("acme/checkout", "appliesDiscount",
                TestStatus.SUSPICIOUS, TestStatus.FLAKY));
        same.put("newStatus", "SUSPICIOUS");

        assertThatThrownBy(() -> listener.parse(same.toString())).isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectsAnUnknownSchemaVersion() {
        ObjectNode future = jsonMapper.valueToTree(TestStatusChangedEvents.event("acme/checkout", "appliesDiscount",
                TestStatus.SUSPICIOUS, TestStatus.FLAKY));
        future.put("schemaVersion", 2);

        assertThatThrownBy(() -> listener.parse(future.toString()))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("schema version 2");
    }

    @Test
    void rejectsAnEmptyEvent() {
        assertThatThrownBy(() -> listener.parse("null")).isInstanceOf(InvalidEventException.class);
    }
}
