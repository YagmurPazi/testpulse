package io.github.yagmurpazi.testpulse.ingestion.domain;

/** Final outcome of a test case. */
public enum TestOutcome {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED;

    /** Whether the test ran and did not pass. */
    public boolean isFailure() {
        return this == FAILED || this == ERROR;
    }
}
