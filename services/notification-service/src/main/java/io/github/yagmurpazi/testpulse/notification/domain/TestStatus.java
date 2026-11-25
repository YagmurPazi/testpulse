package io.github.yagmurpazi.testpulse.notification.domain;

/** The statuses analysis-service assigns to a test (ADR-0007). */
public enum TestStatus {
    STABLE,
    SUSPICIOUS,
    FLAKY,
    QUARANTINED
}
