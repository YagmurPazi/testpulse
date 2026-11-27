package io.github.yagmurpazi.testpulse.ingestion.adapter.in.web;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestionResult;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ReportFile;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsUseCase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload of JUnit XML reports.
 *
 * <p>{@code 201 Created} for a new run, {@code 200 OK} when the same run was uploaded before (idempotent).
 */
@RestController
@RequestMapping("/api/v1/test-runs")
class TestRunController {

    private final UploadReportsUseCase uploadReports;

    TestRunController(UploadReportsUseCase uploadReports) {
        this.uploadReports = uploadReports;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TestRunResponse> upload(
            @RequestPart("reports") List<MultipartFile> reports,
            @RequestParam String repository,
            @RequestParam String commitSha,
            @RequestParam String branch,
            @RequestParam(required = false) String externalRunId,
            @RequestParam(defaultValue = "1") int attempt) throws IOException {

        List<ReportFile> files = new ArrayList<>();
        for (MultipartFile report : reports) {
            String originalName = report.getOriginalFilename();
            String name = originalName == null || originalName.isBlank() ? report.getName() : originalName;
            files.add(new ReportFile(name, report.getBytes()));
        }

        IngestionResult result = uploadReports.upload(
                new UploadReportsCommand(repository, commitSha, branch, externalRunId, attempt, files));

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(TestRunResponse.from(result));
    }
}
