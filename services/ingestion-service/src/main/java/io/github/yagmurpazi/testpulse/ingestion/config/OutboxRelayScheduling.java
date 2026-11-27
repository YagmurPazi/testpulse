package io.github.yagmurpazi.testpulse.ingestion.config;

import io.github.yagmurpazi.testpulse.ingestion.adapter.out.outbox.OutboxRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the outbox relay periodically. Disabled in integration tests, which call the relay directly so they stay
 * deterministic.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "testpulse.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduling {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelayScheduling.class);

    private final OutboxRelay relay;

    OutboxRelayScheduling(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${testpulse.outbox.relay.poll-interval}")
    void relayPendingEvents() {
        try {
            relay.relayPending();
        } catch (OutboxRelay.OutboxRelayException e) {
            LOG.warn("Outbox relay failed, events stay pending and are retried: {}", e.getMessage());
        }
    }
}
