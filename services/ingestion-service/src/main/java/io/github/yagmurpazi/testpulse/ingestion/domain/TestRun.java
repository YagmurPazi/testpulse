package io.github.yagmurpazi.testpulse.ingestion.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A test run as stored by TestPulse: one CI run attempt with all its test results. Immutable once created.
 *
 * @param id         TestPulse identifier
 * @param identity   CI identity; unique across all runs
 * @param commitSha  commit the tests ran against
 * @param branch     branch name
 * @param ingestedAt when TestPulse stored the run
 * @param results    test case results, never empty
 */
public record TestRun(
        UUID id,
        RunIdentity identity,
        CommitSha commitSha,
        String branch,
        Instant ingestedAt,
        List<TestCaseResult> results) {

    public TestRun {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        if (branch.isBlank()) {
            throw new IllegalArgumentException("branch must not be blank");
        }
        results = List.copyOf(results);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("a test run must contain at least one test result");
        }
    }

    public RunSummary summary() {
        return RunSummary.of(results);
    }
}
