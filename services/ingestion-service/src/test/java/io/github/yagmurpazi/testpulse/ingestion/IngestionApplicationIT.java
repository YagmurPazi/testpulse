package io.github.yagmurpazi.testpulse.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Smoke test: the service starts against a real PostgreSQL and Flyway runs. */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class IngestionApplicationIT {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private Flyway flyway;

    @Test
    void connectsToPostgres() {
        String version = jdbcClient.sql("SHOW server_version").query(String.class).single();

        assertThat(version).startsWith("18.");
    }

    @Test
    void runsFlywayOnStartup() {
        assertThat(flyway.info()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
    }
}
