package io.github.yagmurpazi.testpulse.analysis;

import io.github.yagmurpazi.testpulse.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL and Kafka for integration tests, same versions as compose.yaml. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

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

    /** In production ingestion-service creates this topic; here the test context does. */
    @Bean
    NewTopic testRunsTopic() {
        return TopicBuilder.name(Topics.TEST_RUNS).partitions(3).replicas(1).build();
    }
}
