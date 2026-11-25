package io.github.yagmurpazi.testpulse.analysis.domain;

/** Health status of a monitored test. Persisted and published; the behaviour lives in {@link TestState}. */
public enum TestStatus {
    STABLE,
    SUSPICIOUS,
    FLAKY,
    QUARANTINED
}
