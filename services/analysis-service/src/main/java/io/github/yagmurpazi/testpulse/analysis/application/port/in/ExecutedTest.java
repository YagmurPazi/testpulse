package io.github.yagmurpazi.testpulse.analysis.application.port.in;

import io.github.yagmurpazi.testpulse.analysis.domain.Outcome;
import java.util.Objects;

/**
 * Result of one test in an incoming run.
 *
 * @param className      fully qualified test class name
 * @param testName       test method or display name
 * @param outcome        final outcome
 * @param rerunFailures  failed attempts before the final outcome within the run
 * @param durationMillis execution time
 */
public record ExecutedTest(String className, String testName, Outcome outcome, int rerunFailures,
        long durationMillis) {

    public ExecutedTest {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(testName, "testName");
        Objects.requireNonNull(outcome, "outcome");
    }
}
