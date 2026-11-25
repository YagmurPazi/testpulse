package io.github.yagmurpazi.testpulse.ingestion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestionResult;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ReportFile;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.InvalidReportException;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.ReportParser;
import io.github.yagmurpazi.testpulse.ingestion.domain.CommitSha;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestData;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class UploadReportsServiceTest {

    private final CapturingIngestUseCase ingest = new CapturingIngestUseCase();
    private final UploadReportsService service = new UploadReportsService(new LineParser(), ingest);

    @Test
    void ingestsResultsOfAllReportsAsOneRun() {
        service.upload(command("42", report("a.xml", "first\nsecond"), report("b.xml", "third")));

        IngestTestRunCommand ingested = ingest.lastCommand;
        assertThat(ingested.identity()).isEqualTo(new RunIdentity("acme/shop", "42", 3));
        assertThat(ingested.commitSha()).isEqualTo(new CommitSha("abcdef1"));
        assertThat(ingested.branch()).isEqualTo("main");
        assertThat(ingested.results()).extracting(TestCaseResult::testName)
                .containsExactly("first", "second", "third");
    }

    @Test
    void derivesExternalRunIdFromContentWhenNoneIsGiven() {
        service.upload(command(null, report("a.xml", "first")));

        assertThat(ingest.lastCommand.identity().externalRunId())
                .startsWith(UploadReportsService.CONTENT_HASH_PREFIX)
                .hasSize(UploadReportsService.CONTENT_HASH_PREFIX.length() + 64);
    }

    @Test
    void treatsBlankExternalRunIdAsMissing() {
        service.upload(command("  ", report("a.xml", "first")));

        assertThat(ingest.lastCommand.identity().externalRunId()).startsWith("sha256:");
    }

    @Test
    void contentHashIgnoresFileOrderAndNamesButNotContent() {
        String forward = UploadReportsService.contentHash(List.of(report("a", "x"), report("b", "y")));
        String reversed = UploadReportsService.contentHash(List.of(report("c", "y"), report("d", "x")));
        String different = UploadReportsService.contentHash(List.of(report("a", "x"), report("b", "z")));

        assertThat(forward).isEqualTo(reversed).isNotEqualTo(different);
    }

    @Test
    void namesTheFileThatCouldNotBeParsed() {
        assertThatThrownBy(() -> service.upload(command("42", report("a.xml", "ok"), report("broken.xml", "!"))))
                .isInstanceOf(InvalidReportException.class)
                .hasMessage("broken.xml: unreadable")
                .hasCauseInstanceOf(InvalidReportException.class);
        assertThat(ingest.lastCommand).isNull();
    }

    @Test
    void wrapsIoProblemsWhileReadingAReport() {
        UploadReportsService failingRead = new UploadReportsService(input -> {
            throw new UncheckedIOException(new IOException("disk gone"));
        }, ingest);

        assertThatThrownBy(() -> failingRead.upload(command("42", report("a.xml", "x"))))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void rejectsUploadWithoutReports() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.upload(command("42")))
                .withMessageContaining("at least one report");
    }

    @Test
    void rejectsInvalidMetadataBeforeParsing() {
        UploadReportsCommand invalidRepository = new UploadReportsCommand("not-a-repo", "abcdef1", "main", "42", 1,
                List.of(report("a.xml", "!")));

        assertThatIllegalArgumentException().isThrownBy(() -> service.upload(invalidRepository));
    }

    @Test
    void returnsTheResultOfTheIngestion() {
        IngestionResult result = service.upload(command("42", report("a.xml", "first")));

        assertThat(result).isSameAs(ingest.lastResult);
    }

    private static UploadReportsCommand command(String externalRunId, ReportFile... reports) {
        return new UploadReportsCommand("acme/shop", "abcdef1", "main", externalRunId, 3, Arrays.asList(reports));
    }

    private static ReportFile report(String name, String content) {
        return new ReportFile(name, content.getBytes(StandardCharsets.UTF_8));
    }

    /** Test double: every line is a passed test; a line "!" makes the report invalid. */
    private static final class LineParser implements ReportParser {

        @Override
        public List<TestCaseResult> parse(InputStream report) {
            try {
                String content = new String(report.readAllBytes(), StandardCharsets.UTF_8);
                if (content.contains("!")) {
                    throw new InvalidReportException("unreadable");
                }
                return content.lines().map(TestData::passed).toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Test double: records the command and answers with a created run. */
    private static final class CapturingIngestUseCase implements IngestTestRunUseCase {

        private IngestTestRunCommand lastCommand;
        private IngestionResult lastResult;

        @Override
        public IngestionResult ingest(IngestTestRunCommand command) {
            lastCommand = command;
            lastResult = IngestionResult.created(TestData.run(command.identity(), command.results()));
            return lastResult;
        }
    }
}
