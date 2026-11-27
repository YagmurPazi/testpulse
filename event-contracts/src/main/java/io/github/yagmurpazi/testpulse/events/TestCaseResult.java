package io.github.yagmurpazi.testpulse.events;

import java.util.Objects;

/**
 * Result of one test case within a {@link TestRunIngested} event.
 *
 * @param className      fully qualified test class name
 * @param testName       test method or display name
 * @param outcome        final outcome
 * @param durationMillis execution time in milliseconds
 * @param rerunFailures  failed attempts before the final outcome; Surefire reruns produce these.
 *                       A {@code PASSED} test with {@code rerunFailures > 0} was flaky within this very run.
 * @param failureMessage first line of the failure message, truncated by the producer; {@code null} when passed
 */
public record TestCaseResult(
        String className,
        String testName,
        TestOutcome outcome,
        long durationMillis,
        int rerunFailures,
        String failureMessage) {

    /** Maximum length of {@link #failureMessage()}; keeps events well below Kafka's message size limit. */
    public static final int MAX_FAILURE_MESSAGE_LENGTH = 2_000;

    public TestCaseResult {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(testName, "testName");
        Objects.requireNonNull(outcome, "outcome");
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }
        if (rerunFailures < 0) {
            throw new IllegalArgumentException("rerunFailures must not be negative");
        }
        if (failureMessage != null && failureMessage.length() > MAX_FAILURE_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("failureMessage longer than " + MAX_FAILURE_MESSAGE_LENGTH);
        }
    }
}
