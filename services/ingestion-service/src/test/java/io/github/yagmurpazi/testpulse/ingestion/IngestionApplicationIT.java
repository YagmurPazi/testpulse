package io.github.yagmurpazi.testpulse.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Smoke test: the service starts against a real PostgreSQL and all migrations are applied. */
class IngestionApplicationIT extends IntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void connectsToPostgres() {
        String version = jdbc.sql("SHOW server_version").query(String.class).single();

        assertThat(version).startsWith("18.");
    }

    @Test
    void appliesAllMigrationsOnStartup() {
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }
}
