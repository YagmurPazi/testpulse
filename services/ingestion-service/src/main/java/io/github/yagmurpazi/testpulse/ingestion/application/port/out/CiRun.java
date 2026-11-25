package io.github.yagmurpazi.testpulse.ingestion.application.port.out;

import java.time.Instant;
import java.util.Objects;

/**
 * A completed run in a CI system.
 *
 * @param runId       the CI system's run identifier, becomes the run's external id
 * @param attempt     re-runs of the same run are separate attempts
 * @param completedAt when the run finished; runs are imported in this order
 */
public record CiRun(String repository, String runId, int attempt, String commitSha, String branch,
        Instant completedAt) {

    public CiRun {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(completedAt, "completedAt");
    }
}
