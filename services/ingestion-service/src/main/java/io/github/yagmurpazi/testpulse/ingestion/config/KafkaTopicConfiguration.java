package io.github.yagmurpazi.testpulse.ingestion.config;

import io.github.yagmurpazi.testpulse.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics owned by this service. The producer declares its topic; the broker does not create topics implicitly
 * ({@code auto.create.topics.enable=false}), so a typo in a topic name fails instead of creating a new topic.
 */
@Configuration(proxyBeanMethods = false)
class KafkaTopicConfiguration {

    /**
     * Partitions bound parallelism: at most one consumer per partition in a group. Records with the same key
     * (repository) always go to the same partition, which keeps their order.
     *
     * <p>Retention is unlimited: the topic is the replayable history of all runs (ADR-0004), and events are small.
     */
    @Bean
    NewTopic testRunsTopic(
            @Value("${testpulse.kafka.test-runs.partitions}") int partitions,
            @Value("${testpulse.kafka.test-runs.replication-factor}") short replicationFactor) {
        return TopicBuilder.name(Topics.TEST_RUNS)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config(TopicConfig.RETENTION_MS_CONFIG, "-1")
                .build();
    }
}
