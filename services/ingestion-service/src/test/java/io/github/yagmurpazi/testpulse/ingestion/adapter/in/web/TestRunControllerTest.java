package io.github.yagmurpazi.testpulse.ingestion.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestionResult;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.InvalidReportException;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestData;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** HTTP contract of the upload endpoint; the use case is mocked. */
@WebMvcTest(TestRunController.class)
class TestRunControllerTest {

    private static final MockMultipartFile REPORT = new MockMultipartFile(
            "reports", "TEST-a.xml", MediaType.APPLICATION_XML_VALUE, "<testsuite/>".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UploadReportsUseCase uploadReports;

    private final TestRun run = TestData.run(TestData.IDENTITY,
            List.of(TestData.passed("a"), TestData.passedOnRerun("b"), TestData.failed("c")));

    @Test
    void answersCreatedWithRunSummaryForNewRun() {
        given(uploadReports.upload(any())).willReturn(IngestionResult.created(run));

        MvcTestResult result = upload("attempt", "2");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.runId").isEqualTo(run.id().toString());
        assertThat(result).bodyJson().extractingPath("$.created").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.summary.total").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.summary.passedOnRerun").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.summary.failed").isEqualTo(1);
    }

    @Test
    void answersOkForRunThatWasAlreadyIngested() {
        given(uploadReports.upload(any())).willReturn(IngestionResult.alreadyIngested(run));

        MvcTestResult result = upload();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.created").isEqualTo(false);
    }

    @Test
    void passesFilesAndMetadataToTheUseCase() {
        given(uploadReports.upload(any())).willReturn(IngestionResult.created(run));

        upload("externalRunId", "99", "attempt", "2");

        ArgumentCaptor<UploadReportsCommand> command = ArgumentCaptor.forClass(UploadReportsCommand.class);
        verify(uploadReports).upload(command.capture());
        assertThat(command.getValue().repository()).isEqualTo("acme/shop");
        assertThat(command.getValue().commitSha()).isEqualTo("abcdef1");
        assertThat(command.getValue().branch()).isEqualTo("main");
        assertThat(command.getValue().externalRunId()).isEqualTo("99");
        assertThat(command.getValue().attempt()).isEqualTo(2);
        assertThat(command.getValue().reports()).singleElement().satisfies(file -> {
            assertThat(file.name()).isEqualTo("TEST-a.xml");
            assertThat(file.content()).isEqualTo(REPORT.getBytes());
        });
    }

    @Test
    void defaultsAttemptToOne() {
        given(uploadReports.upload(any())).willReturn(IngestionResult.created(run));

        upload();

        ArgumentCaptor<UploadReportsCommand> command = ArgumentCaptor.forClass(UploadReportsCommand.class);
        verify(uploadReports).upload(command.capture());
        assertThat(command.getValue().attempt()).isEqualTo(1);
        assertThat(command.getValue().externalRunId()).isNull();
    }

    @Test
    void answersProblemDetailForUnreadableReport() {
        given(uploadReports.upload(any())).willThrow(new InvalidReportException("TEST-a.xml: Malformed XML report"));

        MvcTestResult result = upload();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid report");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("TEST-a.xml: Malformed XML report");
    }

    @Test
    void answersProblemDetailForInvalidMetadata() {
        given(uploadReports.upload(any())).willThrow(new IllegalArgumentException("not a commit hash: xyz"));

        MvcTestResult result = upload();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid request");
    }

    @Test
    void answersProblemDetailWhenNoReportIsAttached() {
        MvcTestResult result = mvc.post().uri("/api/v1/test-runs").multipart()
                .param("repository", "acme/shop").param("commitSha", "abcdef1").param("branch", "main")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void answersProblemDetailWhenRequiredMetadataIsMissing() {
        MvcTestResult result = mvc.post().uri("/api/v1/test-runs").multipart().file(REPORT)
                .param("repository", "acme/shop").param("branch", "main")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("commitSha");
    }

    @Test
    void answersProblemDetailForNonNumericAttempt() {
        MvcTestResult result = upload("attempt", "second");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private MvcTestResult upload(String... extraParams) {
        var request = mvc.post().uri("/api/v1/test-runs").multipart().file(REPORT)
                .param("repository", "acme/shop").param("commitSha", "abcdef1").param("branch", "main");
        for (int i = 0; i < extraParams.length; i += 2) {
            request = request.param(extraParams[i], extraParams[i + 1]);
        }
        return request.exchange();
    }
}
