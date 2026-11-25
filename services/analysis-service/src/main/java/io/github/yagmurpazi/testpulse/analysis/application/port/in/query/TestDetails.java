package io.github.yagmurpazi.testpulse.analysis.application.port.in.query;

import io.github.yagmurpazi.testpulse.analysis.domain.Outcome;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A test with its recent executions (newest first) and all status changes (oldest first). */
public record TestDetails(UUID testId, String repository, String className, String testName, TestStatus status,
        Instant statusSince, List<Execution> executions, List<Change> statusChanges) {

    public TestDetails {
        executions = List.copyOf(executions);
        statusChanges = List.copyOf(statusChanges);
    }

    public record Execution(UUID runId, String commitSha, int attempt, Outcome outcome, int rerunFailures,
            long durationMillis, Instant executedAt) {
    }

    public record Change(TestStatus from, TestStatus to, String reason, Instant changedAt, UUID triggeringRunId) {
    }
}
