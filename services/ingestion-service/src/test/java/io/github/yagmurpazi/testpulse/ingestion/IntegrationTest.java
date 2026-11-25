package io.github.yagmurpazi.testpulse.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Base for integration tests against real PostgreSQL and Kafka. All subclasses share one Spring context and
 * therefore one set of containers; tables are emptied before each test.
 *
 * <p>The scheduled outbox relay is disabled so tests decide when events are published.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // No relay, so tests decide when events are published; no importer, so tests never call GitHub.
        properties = {"testpulse.outbox.relay.enabled=false", "testpulse.importer.github.token="})
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("TRUNCATE test_run, outbox_event CASCADE").update();
    }

    protected int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }
}
