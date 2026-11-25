package io.github.yagmurpazi.testpulse.ingestion.config;

import io.github.yagmurpazi.testpulse.ingestion.adapter.in.scheduling.CiImportScheduler;
import io.github.yagmurpazi.testpulse.ingestion.adapter.out.github.GitHubActionsClient;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ImportCiRunsUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.CiRunSource;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunRepository;
import io.github.yagmurpazi.testpulse.ingestion.application.service.ImportCiRunsService;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** The GitHub Actions importer (ADR-0012); active only when a token is configured. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${testpulse.importer.github.token:}'.isBlank()")
@EnableConfigurationProperties(GitHubImportProperties.class)
@EnableScheduling
class GitHubImportConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubImportConfiguration.class);

    @Bean
    CiRunSource gitHubActionsClient(GitHubImportProperties properties, JsonMapper jsonMapper) {
        RestClient api = RestClient.builder()
                .requestFactory(requestFactory())
                .baseUrl(properties.apiUrl().toString())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "testpulse-importer")
                .build();
        RestClient storage = RestClient.builder().requestFactory(requestFactory()).build();
        return new GitHubActionsClient(api, storage, jsonMapper, properties.token(), properties.artifactPrefix(),
                properties.runsPerImport(), properties.maxArtifactSize().toBytes());
    }

    @Bean
    ImportCiRunsUseCase importCiRunsUseCase(CiRunSource ciRunSource, TestRunRepository testRuns,
            UploadReportsUseCase uploadReports) {
        return new ImportCiRunsService(ciRunSource, testRuns, uploadReports);
    }

    @Bean
    CiImportScheduler ciImportScheduler(ImportCiRunsUseCase importCiRuns, GitHubImportProperties properties) {
        LOG.info("Importing GitHub Actions runs of {}", properties.repositories());
        return new CiImportScheduler(importCiRuns, properties.repositories());
    }

    /** Redirects are not followed automatically, so the token never leaves GitHub's API (see the client). */
    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(60));
        return factory;
    }
}
