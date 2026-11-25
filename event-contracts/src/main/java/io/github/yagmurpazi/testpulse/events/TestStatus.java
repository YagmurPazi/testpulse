package io.github.yagmurpazi.testpulse.events;

/** Health status of a test as decided by analysis-service. */
public enum TestStatus {
    /** No sign of flakiness. */
    STABLE,
    /** A weak signal was observed; the test is being watched. */
    SUSPICIOUS,
    /** Strong evidence of flakiness. */
    FLAKY,
    /** The team decided to stop trusting this test's results until it is fixed. */
    QUARANTINED
}
