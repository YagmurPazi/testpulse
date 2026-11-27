package io.github.yagmurpazi.testpulse.events;

/** Final outcome of a single test case in a run, as reported by JUnit XML. */
public enum TestOutcome {
    /** The test passed, possibly after failed reruns (see {@link TestCaseResult#rerunFailures()}). */
    PASSED,
    /** An assertion failed. */
    FAILED,
    /** The test threw an unexpected exception. */
    ERROR,
    /** The test was not executed. */
    SKIPPED
}
