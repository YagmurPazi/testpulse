package io.github.yagmurpazi.testpulse.ingestion.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.ingestion.IntegrationTest;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Real HTTP uploads of this repository's own CI reports, through to PostgreSQL and the outbox. */
class UploadTestRunsIT extends IntegrationTest {

    private static final String FAILSAFE = "/reports/ci/failsafe-ingestion-it.xml";
    private static final String SUREFIRE = "/reports/ci/surefire-architecture-rules.xml";
    private static final String XXE = "/reports/samples/xxe-external-entity.xml";

    @LocalServerPort
    private int port;

    private RestClient client;

    @BeforeEach
    void createClient() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    void storesUploadedRunAndAnswersCreated() throws IOException {
        ResponseEntity<JsonNode> response = upload("45619835962", FAILSAFE, SUREFIRE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("summary").path("total").asInt()).isEqualTo(7);
        assertThat(response.getBody().path("externalRunId").asString()).isEqualTo("45619835962");
        assertThat(count("test_run")).isEqualTo(1);
        assertThat(count("test_case_result")).isEqualTo(7);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void answersOkWithSameRunWhenTheSameRunIsUploadedAgain() throws IOException {
        JsonNode first = upload("45619835962", FAILSAFE, SUREFIRE).getBody();

        ResponseEntity<JsonNode> second = upload("45619835962", FAILSAFE, SUREFIRE);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().path("runId")).isEqualTo(first.path("runId"));
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void recognisesTheSameFilesWithoutExternalRunIdRegardlessOfOrder() throws IOException {
        JsonNode first = upload(null, FAILSAFE, SUREFIRE).getBody();

        ResponseEntity<JsonNode> reordered = upload(null, SUREFIRE, FAILSAFE);

        assertThat(first.path("externalRunId").asString()).startsWith("sha256:");
        assertThat(reordered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reordered.getBody().path("runId")).isEqualTo(first.path("runId"));
    }

    @Test
    void rejectsMaliciousReportWithoutStoringAnything() throws IOException {
        ResponseEntity<JsonNode> response = upload("1", FAILSAFE, XXE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().path("title").asString()).isEqualTo("Invalid report");
        assertThat(response.getBody().path("detail").asString()).startsWith("xxe-external-entity.xml:");
        assertThat(count("test_run")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    private ResponseEntity<JsonNode> upload(String externalRunId, String... resources) throws IOException {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        for (String resource : resources) {
            form.add("reports", file(resource));
        }
        form.add("repository", "YagmurPazi/testpulse");
        form.add("commitSha", "3be2117c0ffee0000000000000000000000000ab");
        form.add("branch", "main");
        if (externalRunId != null) {
            form.add("externalRunId", externalRunId);
        }
        return client.post().uri("/api/v1/test-runs")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(JsonNode.class);
    }

    private static ByteArrayResource file(String resource) throws IOException {
        try (InputStream input = UploadTestRunsIT.class.getResourceAsStream(resource)) {
            byte[] content = input.readAllBytes();
            String filename = resource.substring(resource.lastIndexOf('/') + 1);
            return new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        }
    }
}
