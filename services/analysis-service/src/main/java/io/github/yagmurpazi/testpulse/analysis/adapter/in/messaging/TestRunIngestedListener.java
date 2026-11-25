package io.github.yagmurpazi.testpulse.analysis.adapter.in.messaging;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.ExecutedTest;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunCommand;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunUseCase;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessingResult;
import io.github.yagmurpazi.testpulse.analysis.domain.Outcome;
import io.github.yagmurpazi.testpulse.analysis.domain.TestKey;
import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import io.github.yagmurpazi.testpulse.events.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes {@code TestRunIngested} events. The offset is committed only after {@link #onTestRunIngested} returns,
 * so a crash leads to redelivery, which the use case absorbs through its processed-event check.
 */
@Component
class TestRunIngestedListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestRunIngestedListener.class);

    private final ProcessTestRunUseCase processTestRun;
    private final JsonMapper jsonMapper;

    TestRunIngestedListener(ProcessTestRunUseCase processTestRun, JsonMapper jsonMapper) {
        this.processTestRun = processTestRun;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = Topics.TEST_RUNS)
    void onTestRunIngested(ConsumerRecord<String, String> record) {
        ProcessingResult result = processTestRun.process(toCommand(parse(record.value())));
        if (result.duplicate()) {
            LOG.info("Skipped redelivered event at {}-{}@{}", record.topic(), record.partition(), record.offset());
        } else if (!result.changes().isEmpty()) {
            LOG.info("Run at {}-{}@{} changed the status of {} test(s)", record.topic(), record.partition(),
                    record.offset(), result.changes().size());
        }
    }

    TestRunIngested parse(String json) {
        TestRunIngested event;
        try {
            event = jsonMapper.readValue(json, TestRunIngested.class);
        } catch (JacksonException e) {
            // includes violations of the event's own validation, which Jackson wraps
            throw new InvalidEventException("Unreadable TestRunIngested event: " + e.getMessage(), e);
        }
        if (event == null) {
            throw new InvalidEventException("Empty TestRunIngested event");
        }
        if (event.schemaVersion() != TestRunIngested.SCHEMA_VERSION) {
            throw new InvalidEventException("Unsupported TestRunIngested schema version " + event.schemaVersion());
        }
        return event;
    }

    /**
     * Maps the event to the use case's command and validates what the contract does not, such as blank test
     * names. Invalid content must fail here, as a permanent error, and not inside the use case, where it would be
     * retried forever.
     */
    static ProcessTestRunCommand toCommand(TestRunIngested event) {
        try {
            for (var result : event.results()) {
                new TestKey(event.repository(), result.className(), result.testName());
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("Invalid TestRunIngested event " + event.eventId() + ": "
                    + e.getMessage(), e);
        }
        return new ProcessTestRunCommand(event.eventId(), event.runId(), event.repository(), event.commitSha(),
                event.attempt(), event.occurredAt(), event.results().stream()
                        .map(result -> new ExecutedTest(result.className(), result.testName(),
                                Outcome.valueOf(result.outcome().name()), result.rerunFailures(),
                                result.durationMillis()))
                        .toList());
    }
}
