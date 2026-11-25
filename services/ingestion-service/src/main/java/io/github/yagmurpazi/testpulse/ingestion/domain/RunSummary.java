package io.github.yagmurpazi.testpulse.ingestion.domain;

import java.util.List;

/** Counts of outcomes in a run. */
public record RunSummary(int total, int passed, int failed, int errors, int skipped, int passedOnRerun) {

    static RunSummary of(List<TestCaseResult> results) {
        return new RunSummary(
                results.size(),
                count(results, TestOutcome.PASSED),
                count(results, TestOutcome.FAILED),
                count(results, TestOutcome.ERROR),
                count(results, TestOutcome.SKIPPED),
                (int) results.stream().filter(TestCaseResult::passedOnRerun).count());
    }

    private static int count(List<TestCaseResult> results, TestOutcome outcome) {
        return (int) results.stream().filter(result -> result.outcome() == outcome).count();
    }
}
