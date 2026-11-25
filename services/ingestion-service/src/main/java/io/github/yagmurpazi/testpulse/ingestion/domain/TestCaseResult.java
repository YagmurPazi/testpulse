package io.github.yagmurpazi.testpulse.ingestion.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Result of one test case in a run.
 *
 * @param className      fully qualified test class name
 * @param testName       test method or display name
 * @param outcome        final outcome
 * @param duration       execution time
 * @param rerunFailures  failed attempts before the final outcome within the same run
 * @param failureMessage failure message, {@code null} when the test did not fail
 */
public record TestCaseResult(
        String className,
        String testName,
        TestOutcome outcome,
        Duration duration,
        int rerunFailures,
        String failureMessage) {

    public TestCaseResult {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(testName, "testName");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(duration, "duration");
        if (className.isBlank() || testName.isBlank()) {
            throw new IllegalArgumentException("className and testName must not be blank");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        if (rerunFailures < 0) {
            throw new IllegalArgumentException("rerunFailures must not be negative");
        }
    }

    /** Passed only after failing at least once in the same run. */
    public boolean passedOnRerun() {
        return outcome == TestOutcome.PASSED && rerunFailures > 0;
    }
}
