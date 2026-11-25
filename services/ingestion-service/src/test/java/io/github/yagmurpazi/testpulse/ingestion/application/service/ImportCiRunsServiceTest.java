package io.github.yagmurpazi.testpulse.ingestion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ImportSummary;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestionResult;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ReportFile;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.CiRun;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.CiRunSource;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.InvalidReportException;
import io.github.yagmurpazi.testpulse.ingestion.domain.CommitSha;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestOutcome;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportCiRunsServiceTest {

    private static final String REPOSITORY = "acme/checkout";
    private static final String SHA = "9f2c4e1a7b3d5f60718293a4b5c6d7e8f9012345";

    private final FakeCiRunSource source = new FakeCiRunSource();
    private final InMemoryTestRunRepository testRuns = new InMemoryTestRunRepository();
    private final RecordingUpload upload = new RecordingUpload();
    private final ImportCiRunsService service = new ImportCiRunsService(source, testRuns, upload);

    @Test
    void importsNewRunsOldestFirstWithTheirCiMetadata() {
        CiRun newer = run("1002", 1, "2026-11-25T11:00:00Z");
        CiRun older = run("1001", 2, "2026-11-25T10:00:00Z");
        source.add(newer, report("TEST-B.xml"));
        source.add(older, report("TEST-A.xml"));

        ImportSummary summary = service.importRuns(REPOSITORY);

        assertThat(summary).isEqualTo(new ImportSummary(REPOSITORY, 2, 0, 0, List.of()));
        assertThat(upload.commands).extracting(UploadReportsCommand::externalRunId).containsExactly("1001", "1002");
        UploadReportsCommand first = upload.commands.getFirst();
        assertThat(first.repository()).isEqualTo(REPOSITORY);
        assertThat(first.commitSha()).isEqualTo(SHA);
        assertThat(first.branch()).isEqualTo("main");
        assertThat(first.attempt()).isEqualTo(2);
        assertThat(first.reports()).extracting(ReportFile::name).containsExactly("TEST-A.xml");
    }

    @Test
    void skipsRunsIngestedBeforeWithoutDownloadingThem() {
        CiRun known = run("1001", 1, "2026-11-25T10:00:00Z");
        source.add(known, report("TEST-A.xml"));
        testRuns.saveIfAbsent(storedRun(new RunIdentity(REPOSITORY, "1001", 1)));

        ImportSummary summary = service.importRuns(REPOSITORY);

        assertThat(summary).isEqualTo(new ImportSummary(REPOSITORY, 0, 1, 0, List.of()));
        assertThat(source.downloads).isEmpty();
        assertThat(upload.commands).isEmpty();
    }

    @Test
    void aReRunIsANewAttemptAndImportedAgain() {
        source.add(run("1001", 2, "2026-11-25T10:30:00Z"), report("TEST-A.xml"));
        testRuns.saveIfAbsent(storedRun(new RunIdentity(REPOSITORY, "1001", 1)));

        assertThat(service.importRuns(REPOSITORY).imported()).isEqualTo(1);
    }

    @Test
    void countsRunsWithoutReports() {
        source.add(run("1001", 1, "2026-11-25T10:00:00Z"));

        ImportSummary summary = service.importRuns(REPOSITORY);

        assertThat(summary).isEqualTo(new ImportSummary(REPOSITORY, 0, 0, 1, List.of()));
        assertThat(upload.commands).isEmpty();
    }

    @Test
    void oneBrokenRunDoesNotStopTheOthers() {
        source.add(run("1001", 1, "2026-11-25T10:00:00Z"), report("TEST-broken.xml"));
        source.add(run("1002", 1, "2026-11-25T11:00:00Z"), report("TEST-A.xml"));
        source.failDownloadOf("1003");
        source.add(run("1003", 1, "2026-11-25T12:00:00Z"), report("TEST-C.xml"));
        upload.rejectReportsNamed("TEST-broken.xml");

        ImportSummary summary = service.importRuns(REPOSITORY);

        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.failures()).containsExactly(
                "run 1001 attempt 1: TEST-broken.xml: not a JUnit report",
                "run 1003 attempt 1: artifact download failed");
    }

    @Test
    void failsWhenTheRunsCannotBeListed() {
        source.failListing();

        assertThatThrownBy(() -> service.importRuns(REPOSITORY)).isInstanceOf(IllegalStateException.class);
    }

    private static CiRun run(String runId, int attempt, String completedAt) {
        return new CiRun(REPOSITORY, runId, attempt, SHA, "main", Instant.parse(completedAt));
    }

    private static ReportFile report(String name) {
        return new ReportFile(name, "<testsuite/>".getBytes(StandardCharsets.UTF_8));
    }

    private static TestRun storedRun(RunIdentity identity) {
        return new TestRun(UUID.randomUUID(), identity, new CommitSha(SHA), "main", Instant.EPOCH,
                List.of(new TestCaseResult("com.acme.CartTest", "addsItem", TestOutcome.PASSED, Duration.ofMillis(5),
                        0, null)));
    }

    private static final class FakeCiRunSource implements CiRunSource {

        private final Map<CiRun, List<ReportFile>> runs = new HashMap<>();
        private final List<String> failingDownloads = new ArrayList<>();
        private boolean failListing;
        final List<String> downloads = new ArrayList<>();

        void add(CiRun run, ReportFile... reports) {
            runs.put(run, List.of(reports));
        }

        void failDownloadOf(String runId) {
            failingDownloads.add(runId);
        }

        void failListing() {
            failListing = true;
        }

        @Override
        public List<CiRun> recentCompletedRuns(String repository) {
            if (failListing) {
                throw new IllegalStateException("GitHub is down");
            }
            return List.copyOf(runs.keySet());
        }

        @Override
        public List<ReportFile> downloadReports(CiRun run) {
            downloads.add(run.runId());
            if (failingDownloads.contains(run.runId())) {
                throw new IllegalStateException("artifact download failed");
            }
            return runs.get(run);
        }
    }

    private static final class RecordingUpload implements UploadReportsUseCase {

        final List<UploadReportsCommand> commands = new ArrayList<>();
        private final List<String> rejected = new ArrayList<>();

        void rejectReportsNamed(String name) {
            rejected.add(name);
        }

        @Override
        public IngestionResult upload(UploadReportsCommand command) {
            for (ReportFile report : command.reports()) {
                if (rejected.contains(report.name())) {
                    throw new InvalidReportException(report.name() + ": not a JUnit report");
                }
            }
            commands.add(command);
            return IngestionResult.created(storedRun(
                    new RunIdentity(command.repository(), command.externalRunId(), command.attempt())));
        }
    }
}
