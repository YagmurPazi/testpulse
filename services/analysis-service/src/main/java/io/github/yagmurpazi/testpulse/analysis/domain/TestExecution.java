package io.github.yagmurpazi.testpulse.analysis.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One execution of a test in one CI run.
 *
 * @param runId         run the execution belongs to
 * @param commitSha     commit the test ran against
 * @param attempt       CI attempt of the run
 * @param outcome       final outcome
 * @param rerunFailures failed attempts before the final outcome within the same run
 * @param executedAt    when the run was ingested; executions are ordered by it
 */
public record TestExecution(
        UUID runId,
        String commitSha,
        int attempt,
        Outcome outcome,
        int rerunFailures,
        Instant executedAt) {

    public TestExecution {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(executedAt, "executedAt");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }
        if (rerunFailures < 0) {
            throw new IllegalArgumentException("rerunFailures must not be negative");
        }
    }

    /** Passed only after failing in the same run: a pass and a failure on the same commit within one execution. */
    public boolean passedOnRerun() {
        return outcome == Outcome.PASSED && rerunFailures > 0;
    }

    /** A clean pass: passed at the first attempt. */
    public boolean cleanPass() {
        return outcome == Outcome.PASSED && rerunFailures == 0;
    }

    public String shortCommit() {
        return commitSha.substring(0, Math.min(7, commitSha.length()));
    }
}
