package io.github.yagmurpazi.testpulse.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Base for integration tests against real PostgreSQL and Kafka; one shared context and set of containers, tables
 * emptied before each test. The outbox relay is disabled so tests decide when events are published.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "testpulse.outbox.relay.enabled=false")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("TRUNCATE monitored_test, test_execution, status_change, processed_event, outbox_event CASCADE")
                .update();
    }

    protected int count(String tableAndCondition) {
        return jdbc.sql("SELECT count(*) FROM " + tableAndCondition).query(Integer.class).single();
    }
}
