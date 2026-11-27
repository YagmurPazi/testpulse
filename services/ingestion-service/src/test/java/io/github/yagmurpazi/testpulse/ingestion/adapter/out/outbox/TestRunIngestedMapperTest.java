package io.github.yagmurpazi.testpulse.ingestion.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.events.TestOutcome;
import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestData;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestRunIngestedMapperTest {

    private static final int LIMIT = io.github.yagmurpazi.testpulse.events.TestCaseResult.MAX_FAILURE_MESSAGE_LENGTH;

    @Test
    void mapsRunMetadataAndResults() {
        TestRun run = TestData.run(TestData.IDENTITY, List.of(TestData.passedOnRerun("a"), TestData.failed("b")));
        UUID eventId = UUID.randomUUID();

        TestRunIngested event = TestRunIngestedMapper.toEvent(run, eventId);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.schemaVersion()).isEqualTo(TestRunIngested.SCHEMA_VERSION);
        assertThat(event.occurredAt()).isEqualTo(run.ingestedAt());
        assertThat(event.runId()).isEqualTo(run.id());
        assertThat(event.repository()).isEqualTo("YagmurPazi/testpulse");
        assertThat(event.commitSha()).isEqualTo(run.commitSha().value());
        assertThat(event.branch()).isEqualTo("main");
        assertThat(event.externalRunId()).isEqualTo("45619835962");
        assertThat(event.attempt()).isEqualTo(1);
        assertThat(event.results()).extracting(
                io.github.yagmurpazi.testpulse.events.TestCaseResult::testName,
                io.github.yagmurpazi.testpulse.events.TestCaseResult::outcome,
                io.github.yagmurpazi.testpulse.events.TestCaseResult::durationMillis,
                io.github.yagmurpazi.testpulse.events.TestCaseResult::rerunFailures)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a", TestOutcome.PASSED, 5L, 2),
                        org.assertj.core.groups.Tuple.tuple("b", TestOutcome.FAILED, 5L, 0));
        assertThat(event.results().get(1).failureMessage()).isEqualTo("expected: <1> but was: <2>");
    }

    @Test
    void keepsMessagesUpToTheLimit() {
        String atLimit = "x".repeat(LIMIT);

        assertThat(TestRunIngestedMapper.truncate(atLimit)).isSameAs(atLimit);
        assertThat(TestRunIngestedMapper.truncate(null)).isNull();
    }

    @Test
    void truncatesLongerMessagesToTheLimitWithMarker() {
        String truncated = TestRunIngestedMapper.truncate("x".repeat(LIMIT + 1));

        assertThat(truncated).hasSize(LIMIT).endsWith("…");
    }
}
