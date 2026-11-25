package io.github.yagmurpazi.testpulse.ingestion.application.port.in;

/** Pulls the test reports of a repository's recent CI runs, instead of waiting for uploads. */
public interface ImportCiRunsUseCase {

    /**
     * Ingests every recent completed run of the repository that is not ingested yet and has test reports.
     * Idempotent: runs already ingested are skipped without downloading anything.
     *
     * @throws RuntimeException if the CI system cannot list the runs at all; failures of single runs are counted
     *                          in the summary instead, so one broken run does not stop the others
     */
    ImportSummary importRuns(String repository);
}
