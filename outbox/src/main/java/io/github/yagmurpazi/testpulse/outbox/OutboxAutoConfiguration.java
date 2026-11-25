package io.github.yagmurpazi.testpulse.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Registers the outbox writer and relay in every service that has this module on its classpath. */
@AutoConfiguration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    OutboxWriter outboxWriter(JdbcClient jdbc, JsonMapper jsonMapper) {
        return new OutboxWriter(jdbc, jsonMapper);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcClient jdbc, PlatformTransactionManager transactionManager,
            KafkaOperations<String, String> kafka, OutboxProperties properties) {
        return new OutboxRelay(jdbc, new TransactionTemplate(transactionManager), kafka,
                properties.relay().batchSize(), properties.relay().sendTimeout());
    }

    /** Runs the relay periodically unless {@code testpulse.outbox.relay.enabled=false}. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "testpulse.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {

        private static final Logger LOG = LoggerFactory.getLogger(Scheduling.class);

        private final OutboxRelay relay;

        Scheduling(OutboxRelay relay) {
            this.relay = relay;
        }

        @Scheduled(fixedDelayString = "${testpulse.outbox.relay.poll-interval:1s}")
        void relayPendingEvents() {
            try {
                relay.relayPending();
            } catch (OutboxRelay.OutboxRelayException e) {
                LOG.warn("Outbox relay failed, events stay pending and are retried: {}", e.getMessage());
            }
        }
    }
}
