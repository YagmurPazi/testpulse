package io.github.yagmurpazi.testpulse.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SameCommitConflictStrategyTest {

    private final SameCommitConflictStrategy strategy = new SameCommitConflictStrategy();

    @Test
    void flagsPassAndFailureOnTheSameCommitAsFlaky() {
        TestHistory history = Histories.history().fail("9f2c4e1aaaa").pass("9f2c4e1aaaa").build();

        assertThat(strategy.evaluate(history))
                .isEqualTo(FlakinessSignal.flaky("Passed and failed on the same commit 9f2c4e1"));
    }

    @Test
    void countsErrorsAsFailures() {
        TestHistory history = Histories.history().pass("abc1234").add("abc1234", Outcome.ERROR, 0).build();

        assertThat(strategy.evaluate(history).verdict()).isEqualTo(Verdict.FLAKY);
    }

    @Test
    void flagsPassAfterRerunWithinOneRunAsFlaky() {
        TestHistory history = Histories.history().pass("abc1234").add("4a1b2c3d", Outcome.PASSED, 2).build();

        assertThat(strategy.evaluate(history)).isEqualTo(FlakinessSignal.flaky(
                "Passed only after 2 failed attempt(s) within one run on commit 4a1b2c3"));
    }

    @Test
    void flagsASingleExecutionThatPassedOnlyAfterReruns() {
        assertThat(strategy.evaluate(Histories.of("R")).verdict()).isEqualTo(Verdict.FLAKY);
    }

    @Test
    void reportsTheMostRecentEvidence() {
        TestHistory history = Histories.history()
                .add("old1234", Outcome.PASSED, 1)
                .fail("new1234").pass("new1234")
                .build();

        assertThat(strategy.evaluate(history).reason()).endsWith("new1234");
    }

    @Test
    void ignoresDifferentResultsOnDifferentCommits() {
        TestHistory history = Histories.history().pass("aaa1111").fail("bbb2222").pass("ccc3333").build();

        assertThat(strategy.evaluate(history)).isEqualTo(FlakinessSignal.NONE);
    }

    @Test
    void ignoresSkippedExecutions() {
        TestHistory history = Histories.history().fail("abc1234").add("abc1234", Outcome.SKIPPED, 0).build();

        assertThat(strategy.evaluate(history)).isEqualTo(FlakinessSignal.NONE);
    }

    @Test
    void ignoresRepeatedFailuresOnTheSameCommit() {
        TestHistory history = Histories.history().fail("abc1234").add("abc1234", Outcome.ERROR, 0).build();

        assertThat(strategy.evaluate(history)).isEqualTo(FlakinessSignal.NONE);
    }

    @Test
    void findsNothingInAnEmptyHistory() {
        assertThat(strategy.evaluate(Histories.of(""))).isEqualTo(FlakinessSignal.NONE);
    }
}
