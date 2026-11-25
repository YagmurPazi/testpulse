package io.github.yagmurpazi.testpulse.analysis.config;

import io.github.yagmurpazi.testpulse.analysis.adapter.in.messaging.InvalidEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Error handling of the event consumer (ADR-0008).
 *
 * <ul>
 *   <li><b>Permanent errors</b> ({@link InvalidEventException}): logged and skipped at once. A record that can never
 *       be processed would otherwise block its whole partition (poison message).</li>
 *   <li><b>Everything else</b> (database down, unexpected bugs): retried with exponential backoff, without limit.
 *       Skipping would lose data and break the per-repository order; waiting keeps both.</li>
 * </ul>
 *
 * <p>A dead letter topic for skipped records is planned after the MVP.
 */
@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerConfiguration.class);

    /** Picked up by Spring Boot's listener container factory. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(30_000);
        backOff.setMaxElapsedTime(Long.MAX_VALUE);
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> LOG.error(
                "Skipping record {}-{}@{} that can never be processed: {}", record.topic(), record.partition(),
                record.offset(), NestedExceptionUtils.getMostSpecificCause(exception).getMessage()), backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
