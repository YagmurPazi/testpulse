package io.github.yagmurpazi.testpulse.ingestion.adapter.in.web;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestionResult;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunSummary;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.time.Instant;
import java.util.UUID;

/** API representation of a stored run. Separate from the domain so the API can stay stable when the domain moves. */
record TestRunResponse(
        UUID runId,
        boolean created,
        String repository,
        String commitSha,
        String branch,
        String externalRunId,
        int attempt,
        Instant ingestedAt,
        Summary summary) {

    record Summary(int total, int passed, int failed, int errors, int skipped, int passedOnRerun) {
    }

    static TestRunResponse from(IngestionResult result) {
        TestRun run = result.run();
        RunSummary summary = run.summary();
        return new TestRunResponse(
                run.id(),
                result.created(),
                run.identity().repository(),
                run.commitSha().value(),
                run.branch(),
                run.identity().externalRunId(),
                run.identity().attempt(),
                run.ingestedAt(),
                new Summary(summary.total(), summary.passed(), summary.failed(), summary.errors(),
                        summary.skipped(), summary.passedOnRerun()));
    }
}
