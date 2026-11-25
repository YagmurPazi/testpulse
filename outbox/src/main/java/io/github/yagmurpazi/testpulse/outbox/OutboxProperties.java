package io.github.yagmurpazi.testpulse.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code testpulse.outbox}.
 *
 * @param relay relay settings
 */
@ConfigurationProperties("testpulse.outbox")
public record OutboxProperties(@DefaultValue Relay relay) {

    /**
     * @param enabled      run the relay on a schedule; integration tests switch it off and call it directly
     * @param pollInterval pause between relay rounds
     * @param batchSize    events per database transaction
     * @param sendTimeout  how long to wait for Kafka's acknowledgements of one batch
     */
    public record Relay(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("1s") Duration pollInterval,
            @DefaultValue("100") int batchSize,
            @DefaultValue("10s") Duration sendTimeout) {
    }
}
