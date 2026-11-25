package io.github.yagmurpazi.testpulse.ingestion.adapter.out.outbox;

import io.github.yagmurpazi.testpulse.events.TestCaseResult;
import io.github.yagmurpazi.testpulse.events.TestOutcome;
import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.util.UUID;

/** Translates the domain model into the published event contract. */
final class TestRunIngestedMapper {

    private static final String TRUNCATION_MARKER = "…";

    private TestRunIngestedMapper() {
    }

    static TestRunIngested toEvent(TestRun run, UUID eventId) {
        return new TestRunIngested(
                eventId,
                TestRunIngested.SCHEMA_VERSION,
                run.ingestedAt(),
                run.id(),
                run.identity().repository(),
                run.commitSha().value(),
                run.branch(),
                run.identity().externalRunId(),
                run.identity().attempt(),
                run.results().stream().map(TestRunIngestedMapper::toEvent).toList());
    }

    private static TestCaseResult toEvent(io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult result) {
        return new TestCaseResult(
                result.className(),
                result.testName(),
                TestOutcome.valueOf(result.outcome().name()),
                result.duration().toMillis(),
                result.rerunFailures(),
                truncate(result.failureMessage()));
    }

    static String truncate(String message) {
        int limit = TestCaseResult.MAX_FAILURE_MESSAGE_LENGTH;
        if (message == null || message.length() <= limit) {
            return message;
        }
        return message.substring(0, limit - TRUNCATION_MARKER.length()) + TRUNCATION_MARKER;
    }
}
