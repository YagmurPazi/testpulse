package io.github.yagmurpazi.testpulse.ingestion.adapter.out.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ReportFile;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.CiRun;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

class GitHubActionsClientTest {

    private static final String API = "https://api.github.com";
    private static final String STORAGE = "https://productionresultssa0.blob.core.windows.net/actions-results/abc";

    private final RestClient.Builder apiBuilder = RestClient.builder().baseUrl(API);
    private final RestClient.Builder storageBuilder = RestClient.builder();
    private final MockRestServiceServer github = MockRestServiceServer.bindTo(apiBuilder).build();
    private final MockRestServiceServer storage = MockRestServiceServer.bindTo(storageBuilder).build();
    private final GitHubActionsClient client = new GitHubActionsClient(apiBuilder.build(), storageBuilder.build(),
            JsonMapper.builder().build(), "ghp_token", "junit-reports", 20, 1_000_000);

    private final CiRun run = new CiRun("YagmurPazi/testpulse", "45619835962", 2,
            "2df61a9007755483d0305f636eb7415350ac4710", "main", Instant.parse("2026-11-25T10:00:00Z"));

    @Test
    void listsCompletedRunsWithTheirCiMetadata() {
        github.expect(requestTo(API + "/repos/YagmurPazi/testpulse/actions/runs?status=completed&per_page=20"))
                .andExpect(header("Authorization", "Bearer ghp_token"))
                .andRespond(withSuccess("""
                        {"total_count": 2, "workflow_runs": [
                          {"id": 45619835962, "run_attempt": 2, "head_sha": "2df61a9007755483d0305f636eb7415350ac4710",
                           "head_branch": "main", "status": "completed", "updated_at": "2026-11-25T10:00:00Z"},
                          {"id": 45619835963, "run_attempt": 1, "head_sha": "9f2c4e1a7b3d5f60718293a4b5c6d7e8f9012345",
                           "head_branch": null, "status": "completed", "updated_at": "2026-11-25T11:00:00Z"}
                        ]}""", MediaType.APPLICATION_JSON));

        List<CiRun> runs = client.recentCompletedRuns("YagmurPazi/testpulse");

        assertThat(runs).containsExactly(run, new CiRun("YagmurPazi/testpulse", "45619835963", 1,
                "9f2c4e1a7b3d5f60718293a4b5c6d7e8f9012345", "unknown", Instant.parse("2026-11-25T11:00:00Z")));
        github.verify();
    }

    @Test
    void downloadsTheReportArtifactsFollowingTheRedirectWithoutTheToken() {
        github.expect(requestTo(API + "/repos/YagmurPazi/testpulse/actions/runs/45619835962/artifacts?per_page=100"))
                .andRespond(withSuccess("""
                        {"artifacts": [
                          {"name": "junit-reports", "expired": false, "size_in_bytes": 300,
                           "created_at": "2026-11-25T09:59:00Z",
                           "archive_download_url": "https://api.github.com/repos/YagmurPazi/testpulse/actions/artifacts/1/zip"},
                          {"name": "coverage-reports", "expired": false, "size_in_bytes": 300,
                           "created_at": "2026-11-25T09:59:00Z",
                           "archive_download_url": "https://api.github.com/repos/YagmurPazi/testpulse/actions/artifacts/2/zip"},
                          {"name": "junit-reports-e2e", "expired": true, "size_in_bytes": 300,
                           "created_at": "2026-11-25T09:59:00Z",
                           "archive_download_url": "https://api.github.com/repos/YagmurPazi/testpulse/actions/artifacts/3/zip"}
                        ]}""", MediaType.APPLICATION_JSON));
        github.expect(requestTo(API + "/repos/YagmurPazi/testpulse/actions/artifacts/1/zip"))
                .andExpect(header("Authorization", "Bearer ghp_token"))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI.create(STORAGE)));
        storage.expect(requestTo(STORAGE))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(Zips.zip(Map.of("TEST-a.xml", "<testsuite/>")),
                        MediaType.APPLICATION_OCTET_STREAM));

        List<ReportFile> reports = client.downloadReports(run);

        assertThat(reports).extracting(ReportFile::name).containsExactly("junit-reports/TEST-a.xml");
        github.verify();
        storage.verify();
    }

    @Test
    void takesOnlyTheNewestArtifactOfAName() {
        github.expect(requestTo(API + "/repos/YagmurPazi/testpulse/actions/runs/45619835962/artifacts?per_page=100"))
                .andRespond(withSuccess("""
                        {"artifacts": [
                          {"name": "junit-reports", "expired": false, "size_in_bytes": 300,
                           "created_at": "2026-11-25T10:30:00Z",
                           "archive_download_url": "https://api.github.com/repos/YagmurPazi/testpulse/actions/artifacts/2/zip"},
                          {"name": "junit-reports", "expired": false, "size_in_bytes": 300,
                           "created_at": "2026-11-25T09:59:00Z",
                           "archive_download_url": "https://api.github.com/repos/YagmurPazi/testpulse/actions/artifacts/1/zip"}
                        ]}""", MediaType.APPLICATION_JSON));
        github.expect(ExpectedCount.once(), requestTo(API + "/repos/YagmurPazi/testpulse/actions/artifacts/2/zip"))
                .andRespond(withSuccess(Zips.zip(Map.of("TEST-attempt-2.xml", "<testsuite/>")),
                        MediaType.APPLICATION_OCTET_STREAM));

        assertThat(client.downloadReports(run)).extracting(ReportFile::name)
                .containsExactly("junit-reports/TEST-attempt-2.xml");
        github.verify();
    }

    @Test
    void aRunWithoutReportArtifactsHasNoReports() {
        github.expect(requestTo(API + "/repos/YagmurPazi/testpulse/actions/runs/45619835962/artifacts?per_page=100"))
                .andRespond(withSuccess("{\"artifacts\": []}", MediaType.APPLICATION_JSON));

        assertThat(client.downloadReports(run)).isEmpty();
    }

    @Test
    void refusesAnArtifactLargerThanTheLimitBeforeDownloadingIt() {
        github.expect(requestTo(API + "/repos/YagmurPazi/testpulse/actions/runs/45619835962/artifacts?per_page=100"))
                .andRespond(withSuccess("""
                        {"artifacts": [{"name": "junit-reports", "expired": false, "size_in_bytes": 2000000,
                          "created_at": "2026-11-25T09:59:00Z",
                          "archive_download_url": "https://api.github.com/repos/YagmurPazi/testpulse/actions/artifacts/1/zip"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.downloadReports(run))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("larger than the size limit");
        github.verify();
    }

    @Test
    void reportsAFailedDownload() {
        github.expect(requestTo(API + "/repos/YagmurPazi/testpulse/actions/runs/45619835962/artifacts?per_page=100"))
                .andRespond(withSuccess("""
                        {"artifacts": [{"name": "junit-reports", "expired": false, "size_in_bytes": 300,
                          "created_at": "2026-11-25T09:59:00Z",
                          "archive_download_url": "https://api.github.com/repos/YagmurPazi/testpulse/actions/artifacts/1/zip"}]}
                        """, MediaType.APPLICATION_JSON));
        github.expect(requestTo(API + "/repos/YagmurPazi/testpulse/actions/artifacts/1/zip"))
                .andRespond(withStatus(HttpStatus.GONE));

        assertThatThrownBy(() -> client.downloadReports(run))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("410");
    }

    @Test
    void propagatesAnApiErrorWhenListingRuns() {
        github.expect(requestTo(API + "/repos/YagmurPazi/testpulse/actions/runs?status=completed&per_page=20"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.recentCompletedRuns("YagmurPazi/testpulse"))
                .isInstanceOf(RestClientException.class);
    }
}
