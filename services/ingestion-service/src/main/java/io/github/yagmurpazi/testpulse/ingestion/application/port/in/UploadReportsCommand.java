package io.github.yagmurpazi.testpulse.ingestion.application.port.in;

import java.util.List;
import java.util.Objects;

/**
 * Raw input of an upload: report files plus the CI metadata the client sent. Validated by the use case.
 *
 * @param externalRunId CI run identifier, or {@code null} to derive one from the report contents
 */
public record UploadReportsCommand(
        String repository,
        String commitSha,
        String branch,
        String externalRunId,
        int attempt,
        List<ReportFile> reports) {

    public UploadReportsCommand {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(branch, "branch");
        reports = List.copyOf(reports);
    }
}
