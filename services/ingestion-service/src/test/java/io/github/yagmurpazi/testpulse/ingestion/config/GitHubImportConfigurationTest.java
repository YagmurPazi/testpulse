package io.github.yagmurpazi.testpulse.ingestion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.yagmurpazi.testpulse.ingestion.adapter.in.scheduling.CiImportScheduler;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.UploadReportsUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.CiRunSource;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/** The importer exists only with a token: without one, no GitHub call is ever made. */
class GitHubImportConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(GitHubImportConfiguration.class)
            .withBean(TestRunRepository.class, () -> mock(TestRunRepository.class))
            .withBean(UploadReportsUseCase.class, () -> mock(UploadReportsUseCase.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues("testpulse.importer.github.api-url=https://api.github.com",
                    "testpulse.importer.github.repositories=YagmurPazi/testpulse",
                    "testpulse.importer.github.artifact-prefix=junit-reports",
                    "testpulse.importer.github.runs-per-import=20",
                    "testpulse.importer.github.max-artifact-size=50MB",
                    // never reached: the first import is far in the future
                    "testpulse.importer.github.initial-delay=1h");

    @Test
    void isOffWithoutAToken() {
        context.withPropertyValues("testpulse.importer.github.token=")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(CiRunSource.class);
                    assertThat(ctx).doesNotHaveBean(CiImportScheduler.class);
                });
    }

    @Test
    void isOnWithAToken() {
        context.withPropertyValues("testpulse.importer.github.token=ghp_token")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(CiRunSource.class);
                    assertThat(ctx).hasSingleBean(CiImportScheduler.class);
                });
    }
}
