package io.github.yagmurpazi.testpulse.analysis.domain;

/** Final outcome of one execution of a test. */
public enum Outcome {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED;

    /** The test ran and did not pass. */
    public boolean isFailure() {
        return this == FAILED || this == ERROR;
    }

    /** The test ran, so the execution says something about its health. */
    public boolean ran() {
        return this != SKIPPED;
    }
}
