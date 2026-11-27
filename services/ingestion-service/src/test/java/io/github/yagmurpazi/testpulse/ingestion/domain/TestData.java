package io.github.yagmurpazi.testpulse.ingestion.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Valid sample values for tests. */
public final class TestData {

    public static final RunIdentity IDENTITY = new RunIdentity("YagmurPazi/testpulse", "45619835962", 1);
    public static final CommitSha COMMIT = new CommitSha("3be2117c0ffee0000000000000000000000000ab");
    public static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    private TestData() {
    }

    public static TestCaseResult passed(String testName) {
        return new TestCaseResult("io.example.SampleTest", testName, TestOutcome.PASSED, Duration.ofMillis(5), 0,
                null);
    }

    public static TestCaseResult failed(String testName) {
        return new TestCaseResult("io.example.SampleTest", testName, TestOutcome.FAILED, Duration.ofMillis(5), 0,
                "expected: <1> but was: <2>");
    }

    public static TestCaseResult passedOnRerun(String testName) {
        return new TestCaseResult("io.example.SampleTest", testName, TestOutcome.PASSED, Duration.ofMillis(5), 2,
                null);
    }

    /** A result of any class; a failed one has a stack-trace-like message, as real reports do. */
    public static TestCaseResult result(String className, String testName, boolean failed) {
        if (failed) {
            return new TestCaseResult(className, testName, TestOutcome.FAILED, Duration.ofMillis(12), 0,
                    "expected:<true> but was:<false>\n\tat " + className + ".run(" + testName + ".java:42)");
        }
        return new TestCaseResult(className, testName, TestOutcome.PASSED, Duration.ofMillis(12), 0, null);
    }

    public static TestRun run(RunIdentity identity, List<TestCaseResult> results) {
        return new TestRun(UUID.randomUUID(), identity, COMMIT, "main", NOW, results);
    }
}
