package io.github.yagmurpazi.testpulse.notification.config;

import io.github.yagmurpazi.testpulse.notification.adapter.in.messaging.InvalidEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Error handling of the event consumer (ADR-0011). Unlike analysis-service, retries are bounded: a notification
 * that cannot be delivered for about a minute is given up, because a channel that is down for hours must not hold
 * back every later notification. Losing a message is the smaller harm for a notification; for analysis it would
 * be lost data.
 *
 * <ul>
 *   <li>{@link InvalidEventException}: skipped at once.</li>
 *   <li>Everything else (a channel or the database down): 6 retries, 1 s doubling to 30 s, then skipped.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfiguration {

    static final int MAX_RETRIES = 6;

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerConfiguration.class);

    /** Picked up by Spring Boot's listener container factory. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(30_000);
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> LOG.error(
                "Gave up on record {}-{}@{}: {}", record.topic(), record.partition(), record.offset(),
                NestedExceptionUtils.getMostSpecificCause(exception).getMessage()), backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
