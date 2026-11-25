package io.github.yagmurpazi.testpulse.notification;

import io.github.yagmurpazi.testpulse.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL, Kafka and SMTP server (Mailpit) for integration tests, same versions as compose.yaml. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final int SMTP_PORT = 1025;
    static final int MAILPIT_API_PORT = 8025;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.7");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.4.0");
    }

    @Bean
    GenericContainer<?> mailpit() {
        return new GenericContainer<>("axllent/mailpit:v1.34.0")
                .withExposedPorts(SMTP_PORT, MAILPIT_API_PORT)
                .waitingFor(Wait.forHttp("/livez").forPort(MAILPIT_API_PORT));
    }

    @Bean
    DynamicPropertyRegistrar mailProperties(GenericContainer<?> mailpit) {
        return registry -> {
            registry.add("spring.mail.host", mailpit::getHost);
            registry.add("spring.mail.port", () -> mailpit.getMappedPort(SMTP_PORT));
            registry.add("test.mailpit.url",
                    () -> "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(MAILPIT_API_PORT));
        };
    }

    /** In production analysis-service creates this topic; here the test context does. */
    @Bean
    NewTopic testStatusChangesTopic() {
        return TopicBuilder.name(Topics.TEST_STATUS_CHANGES).partitions(3).replicas(1).build();
    }
}
