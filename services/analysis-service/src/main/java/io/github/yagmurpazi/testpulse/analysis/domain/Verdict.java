package io.github.yagmurpazi.testpulse.analysis.domain;

/** How strongly the evidence points at flakiness. Ordered from weakest to strongest. */
public enum Verdict {
    /** No sign of flakiness. */
    NONE,
    /** Weak evidence: worth watching. */
    SUSPICIOUS,
    /** Strong evidence: the test is flaky. */
    FLAKY;

    public boolean isStrongerThan(Verdict other) {
        return compareTo(other) > 0;
    }
}
