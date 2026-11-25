package io.github.yagmurpazi.testpulse.ingestion.application.service;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ImportCiRunsUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ImportSummary;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ReportFile;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.CiRun;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.CiRunSource;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunRepository;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Imports CI runs through the same path as uploads, so an imported run is identical to an uploaded one, and a run
 * that was uploaded and later imported (or the other way round) is ingested once.
 *
 * <p>Runs are imported oldest first: analysis dates executions with their ingestion time (ADR-0007), so the
 * ingestion order has to follow the order in which the runs happened.
 */
public final class ImportCiRunsService implements ImportCiRunsUseCase {

    private final CiRunSource source;
    private final TestRunRepository testRuns;
    private final UploadReportsUseCase uploadReports;

    public ImportCiRunsService(CiRunSource source, TestRunRepository testRuns, UploadReportsUseCase uploadReports) {
        this.source = Objects.requireNonNull(source, "source");
        this.testRuns = Objects.requireNonNull(testRuns, "testRuns");
        this.uploadReports = Objects.requireNonNull(uploadReports, "uploadReports");
    }

    @Override
    public ImportSummary importRuns(String repository) {
        List<CiRun> runs = source.recentCompletedRuns(repository).stream()
                .sorted(Comparator.comparing(CiRun::completedAt))
                .toList();
        int imported = 0;
        int alreadyIngested = 0;
        int withoutReports = 0;
        List<String> failures = new ArrayList<>();
        for (CiRun run : runs) {
            if (testRuns.findByIdentity(new RunIdentity(repository, run.runId(), run.attempt())).isPresent()) {
                alreadyIngested++;
                continue;
            }
            try {
                List<ReportFile> reports = source.downloadReports(run);
                if (reports.isEmpty()) {
                    withoutReports++;
                    continue;
                }
                uploadReports.upload(new UploadReportsCommand(repository, run.commitSha(), run.branch(),
                        run.runId(), run.attempt(), reports));
                imported++;
            } catch (RuntimeException e) {
                failures.add("run " + run.runId() + " attempt " + run.attempt() + ": " + e.getMessage());
            }
        }
        return new ImportSummary(repository, imported, alreadyIngested, withoutReports, failures);
    }
}
