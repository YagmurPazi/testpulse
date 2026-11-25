package io.github.yagmurpazi.testpulse.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Smoke test: the service starts against real PostgreSQL and Kafka and applies all migrations. */
class AnalysisApplicationIT extends IntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void appliesAllMigrationsOnStartup() {
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }
}
