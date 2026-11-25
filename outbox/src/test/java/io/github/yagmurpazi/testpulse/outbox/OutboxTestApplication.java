package io.github.yagmurpazi.testpulse.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Minimal application for the module's integration tests: real PostgreSQL and Kafka, one three-partition topic. */
@SpringBootApplication
class OutboxTestApplication {

    static final String TOPIC = "outbox-it";

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
    NewTopic topic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }
}
