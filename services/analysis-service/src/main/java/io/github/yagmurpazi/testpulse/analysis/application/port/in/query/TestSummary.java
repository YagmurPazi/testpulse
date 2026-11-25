package io.github.yagmurpazi.testpulse.analysis.application.port.in.query;

import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of a repository's test list.
 *
 * @param lastReason reason of the latest status change, {@code null} if the status never changed
 */
public record TestSummary(UUID testId, String className, String testName, TestStatus status, Instant statusSince,
        String lastReason, int executions, int failures) {
}
