package io.github.yagmurpazi.testpulse.analysis.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.ExecutedTest;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunCommand;
import io.github.yagmurpazi.testpulse.analysis.domain.Outcome;
import io.github.yagmurpazi.testpulse.events.TestCaseResult;
import io.github.yagmurpazi.testpulse.events.TestOutcome;
import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class TestRunIngestedListenerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final TestRunIngestedListener listener = new TestRunIngestedListener(command -> null, jsonMapper);

    @Test
    void readsAValidEvent() {
        TestRunIngested event = TestRunIngestedEvents.event("acme/checkout", "appliesDiscount");

        assertThat(listener.parse(jsonMapper.writeValueAsString(event))).isEqualTo(event);
    }

    @Test
    void mapsTheEventToTheProcessingCommand() {
        TestRunIngested event = new TestRunIngested(java.util.UUID.randomUUID(), 1, java.time.Instant.EPOCH,
                java.util.UUID.randomUUID(), "acme/checkout", "9f2c4e1", "main", "1001", 2, List.of(
                        new TestCaseResult("io.example.CheckoutTest", "slow", TestOutcome.ERROR, 1503, 1, "timeout")));

        ProcessTestRunCommand command = TestRunIngestedListener.toCommand(event);

        assertThat(command).isEqualTo(new ProcessTestRunCommand(event.eventId(), event.runId(), "acme/checkout",
                "9f2c4e1", 2, java.time.Instant.EPOCH,
                List.of(new ExecutedTest("io.example.CheckoutTest", "slow", Outcome.ERROR, 1, 1503))));
    }

    @Test
    void classifiesUnreadableJsonAsInvalid() {
        assertThatThrownBy(() -> listener.parse("not json"))
                .isInstanceOf(InvalidEventException.class).hasMessageStartingWith("Unreadable");
    }

    @Test
    void classifiesAnEventMissingRequiredFieldsAsInvalid() {
        ObjectNode json = jsonMapper.valueToTree(TestRunIngestedEvents.event("acme/checkout", "t"));
        json.remove("runId");

        assertThatThrownBy(() -> listener.parse(json.toString())).isInstanceOf(InvalidEventException.class);
    }

    @Test
    void classifiesAnEmptyPayloadAsInvalid() {
        assertThatThrownBy(() -> listener.parse("null"))
                .isInstanceOf(InvalidEventException.class).hasMessage("Empty TestRunIngested event");
    }

    @Test
    void classifiesAnUnknownSchemaVersionAsInvalid() {
        ObjectNode json = jsonMapper.valueToTree(TestRunIngestedEvents.event("acme/checkout", "t"));
        json.put("schemaVersion", 2);

        assertThatThrownBy(() -> listener.parse(json.toString()))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("schema version 2");
    }

    @Test
    void classifiesBlankTestNamesAsInvalidBeforeTheUseCaseSeesThem() {
        TestRunIngested blankName = TestRunIngestedEvents.event("acme/checkout", " ");

        assertThatThrownBy(() -> TestRunIngestedListener.toCommand(blankName))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining(blankName.eventId().toString());
    }
}
