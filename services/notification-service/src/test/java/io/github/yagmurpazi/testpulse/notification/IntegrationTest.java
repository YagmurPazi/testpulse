package io.github.yagmurpazi.testpulse.notification;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Base for integration tests against real PostgreSQL, Kafka and Mailpit; one shared context and set of
 * containers, deliveries and captured e-mails removed before each test.
 */
@SpringBootTest(properties = "testpulse.notification.slack.webhook-url=")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected JdbcClient jdbc;

    protected Mailpit mailpit;

    @Value("${test.mailpit.url}")
    void mailpitUrl(String url) {
        this.mailpit = new Mailpit(url);
    }

    @BeforeEach
    void cleanUp() {
        jdbc.sql("TRUNCATE delivered_notification").update();
        mailpit.deleteAll();
    }

    protected int count(String tableAndCondition) {
        return jdbc.sql("SELECT count(*) FROM " + tableAndCondition).query(Integer.class).single();
    }
}
