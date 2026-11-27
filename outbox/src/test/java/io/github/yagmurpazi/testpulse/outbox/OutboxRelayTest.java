package io.github.yagmurpazi.testpulse.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.NetworkException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaProducerException;

/** Which failures the relay gives up on; the rest of its behaviour is tested against Kafka in OutboxRelayIT. */
class OutboxRelayTest {

    @Test
    void aRecordLargerThanKafkasLimitCanNeverBeSent() {
        assertThat(OutboxRelay.isPermanent(new RecordTooLargeException("1258291 bytes"))).isTrue();
    }

    @Test
    void recognisesTheLimitAlsoWhenSpringKafkaWrapsIt() {
        KafkaProducerException wrapped = new KafkaProducerException(new ProducerRecord<>("topic", "value"),
                "Failed to send", new RecordTooLargeException("1258291 bytes"));

        assertThat(OutboxRelay.isPermanent(wrapped)).isTrue();
    }

    @Test
    void everythingElseIsRetried() {
        assertThat(OutboxRelay.isPermanent(new NetworkException("broker down"))).isFalse();
        assertThat(OutboxRelay.isPermanent(new TimeoutException("no acknowledgement"))).isFalse();
        assertThat(OutboxRelay.isPermanent(new IllegalStateException("unexpected"))).isFalse();
    }
}
