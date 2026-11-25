package io.github.yagmurpazi.testpulse.ingestion.adapter.out.github;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ReportFile;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.CiRun;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.CiRunSource;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GitHub Actions through its REST API: completed workflow runs and their artifacts whose names start with a
 * prefix ({@code junit-reports} in this repository's own CI).
 *
 * <p>Downloading an artifact answers with a redirect to a short-lived storage URL. The redirect is followed by hand
 * and without the token: the token is for GitHub only and must not reach another host.
 */
public final class GitHubActionsClient implements CiRunSource {

    /** GitHub sends no branch for some events; a run still needs one. */
    static final String UNKNOWN_BRANCH = "unknown";

    private final RestClient api;
    private final RestClient storage;
    private final JsonMapper jsonMapper;
    private final String authorization;
    private final String artifactPrefix;
    private final int runsPerImport;
    private final long maxArtifactBytes;

    /**
     * @param api     client with GitHub's API URL as base URL and its standard headers
     * @param storage client for the redirect target of downloads, with no credentials
     */
    public GitHubActionsClient(RestClient api, RestClient storage, JsonMapper jsonMapper, String token,
            String artifactPrefix, int runsPerImport, long maxArtifactBytes) {
        this.api = Objects.requireNonNull(api, "api");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.authorization = "Bearer " + Objects.requireNonNull(token, "token");
        this.artifactPrefix = Objects.requireNonNull(artifactPrefix, "artifactPrefix");
        this.runsPerImport = runsPerImport;
        this.maxArtifactBytes = maxArtifactBytes;
    }

    @Override
    public List<CiRun> recentCompletedRuns(String repository) {
        String[] ownerAndName = repository.split("/", 2);
        JsonNode body = getJson("/repos/{owner}/{name}/actions/runs?status=completed&per_page={count}",
                ownerAndName[0], ownerAndName[1], runsPerImport);
        return body.path("workflow_runs").valueStream()
                .map(run -> new CiRun(repository, run.path("id").asString(), run.path("run_attempt").asInt(1),
                        run.path("head_sha").asString(), branch(run), Instant.parse(run.path("updated_at").asString())))
                .toList();
    }

    @Override
    public List<ReportFile> downloadReports(CiRun run) {
        String[] ownerAndName = run.repository().split("/", 2);
        JsonNode body = getJson("/repos/{owner}/{name}/actions/runs/{runId}/artifacts?per_page=100",
                ownerAndName[0], ownerAndName[1], run.runId());
        List<ReportFile> reports = new ArrayList<>();
        for (JsonNode artifact : reportArtifacts(body)) {
            String name = artifact.path("name").asString();
            if (artifact.path("size_in_bytes").asLong() > maxArtifactBytes) {
                throw new IllegalStateException("Artifact " + name + " is larger than the size limit");
            }
            byte[] zip = download(URI.create(artifact.path("archive_download_url").asString()));
            reports.addAll(ArtifactArchive.xmlFiles(name, zip, maxArtifactBytes));
        }
        return reports;
    }

    /**
     * Unexpired artifacts with the prefix. A re-run uploads artifacts under the same names again; per name only
     * the newest counts, the one of the latest attempt.
     */
    private List<JsonNode> reportArtifacts(JsonNode body) {
        Map<String, JsonNode> newestByName = new LinkedHashMap<>();
        body.path("artifacts").valueStream()
                .filter(artifact -> artifact.path("name").asString().startsWith(artifactPrefix))
                .filter(artifact -> !artifact.path("expired").asBoolean())
                .sorted(Comparator.comparing(artifact -> artifact.path("created_at").asString()))
                .forEach(artifact -> newestByName.put(artifact.path("name").asString(), artifact));
        return List.copyOf(newestByName.values());
    }

    private byte[] download(URI archiveUrl) {
        return api.get().uri(archiveUrl).header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange((request, response) -> {
                    if (response.getStatusCode().is3xxRedirection()) {
                        URI location = Objects.requireNonNull(response.getHeaders().getLocation(),
                                "redirect without location");
                        return storage.get().uri(location).retrieve().body(byte[].class);
                    }
                    if (response.getStatusCode().is2xxSuccessful()) {
                        return response.getBody().readAllBytes();
                    }
                    throw new IllegalStateException("Artifact download failed with " + response.getStatusCode());
                });
    }

    private JsonNode getJson(String uriTemplate, Object... variables) {
        String json = api.get().uri(uriTemplate, variables).header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve().body(String.class);
        return jsonMapper.readTree(json == null ? "{}" : json);
    }

    private static String branch(JsonNode run) {
        String branch = run.path("head_branch").asString("");
        return branch.isBlank() ? UNKNOWN_BRANCH : branch;
    }
}
