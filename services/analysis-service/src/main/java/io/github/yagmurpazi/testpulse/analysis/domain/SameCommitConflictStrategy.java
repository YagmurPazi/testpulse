package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.List;

/**
 * Strong signal: the code did not change, but the result did.
 *
 * <p>Two forms of evidence: a test that passed and failed on the same commit (for example a CI re-run), and a test
 * that passed only after failing within one run (Surefire reruns). Both mean the outcome depends on something other
 * than the code under test.
 */
public final class SameCommitConflictStrategy implements FlakinessStrategy {

    @Override
    public FlakinessSignal evaluate(TestHistory history) {
        List<TestExecution> ran = history.ran();
        for (int i = ran.size() - 1; i >= 0; i--) {
            TestExecution execution = ran.get(i);
            if (execution.passedOnRerun()) {
                return FlakinessSignal.flaky("Passed only after " + execution.rerunFailures()
                        + " failed attempt(s) within one run on commit " + execution.shortCommit());
            }
            if (conflictsWithAnotherExecution(execution, ran)) {
                return FlakinessSignal.flaky("Passed and failed on the same commit " + execution.shortCommit());
            }
        }
        return FlakinessSignal.NONE;
    }

    private static boolean conflictsWithAnotherExecution(TestExecution execution, List<TestExecution> ran) {
        return ran.stream().anyMatch(other -> other.commitSha().equals(execution.commitSha())
                && other.outcome().isFailure() != execution.outcome().isFailure());
    }
}
