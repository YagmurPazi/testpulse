package io.github.yagmurpazi.testpulse.outbox;

import static io.github.yagmurpazi.testpulse.outbox.OutboxTestApplication.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Writer and relay against real PostgreSQL and Kafka. The table comes from the module's own DDL resource. */
@SpringBootTest(properties = {
    "testpulse.outbox.relay.enabled=false",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:io/github/yagmurpazi/testpulse/outbox/outbox_event.sql"
})
class OutboxRelayIT {

    @Autowired
    private OutboxWriter writer;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void emptyOutbox() {
        jdbc.sql("DELETE FROM outbox_event").update();
    }

    @Test
    void publishesStoredEventAsJsonWithKeyAndEventIdHeader() {
        UUID eventId = UUID.randomUUID();
        writer.append(eventId, TOPIC, "acme/shop", new SampleEvent(eventId, "hello"), Instant.now());

        assertThat(relay.relayPending()).isEqualTo(1);

        ConsumerRecord<String, String> record = consume(withEventId(eventId), 1).getFirst();
        assertThat(record.key()).isEqualTo("acme/shop");
        assertThat(record.value()).isEqualTo("{\"id\":\"" + eventId + "\",\"text\":\"hello\"}");
        assertThat(new String(record.headers().lastHeader(OutboxRelay.EVENT_ID_HEADER).value(),
                StandardCharsets.UTF_8)).isEqualTo(eventId.toString());
        assertThat(pending()).isZero();
    }

    @Test
    void publishesEachEventOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        writer.append(eventId, TOPIC, "acme/shop", new SampleEvent(eventId, "once"), Instant.now());

        relay.relayPending();

        assertThat(relay.relayPending()).isZero();
        assertThat(consume(withEventId(eventId), Integer.MAX_VALUE, Duration.ofSeconds(3))).hasSize(1);
    }

    @Test
    void keepsEventsWithTheSameKeyInOnePartitionInCreationOrder() {
        String key = "acme/ordered-" + UUID.randomUUID();
        Instant base = Instant.parse("2026-11-26T10:00:00Z");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            // written out of order: the relay must publish by creation time, not by insertion order
            writer.append(id, TOPIC, key, new SampleEvent(id, "event" + i), base.plusSeconds(4 - i));
        }

        relay.relayPending();

        List<ConsumerRecord<String, String>> records = consume(record -> key.equals(record.key()), 5);
        assertThat(records).extracting(ConsumerRecord::partition).containsOnly(records.getFirst().partition());
        assertThat(records).extracting(ConsumerRecord::value)
                .containsExactly(json(ids.get(4), "event4"), json(ids.get(3), "event3"), json(ids.get(2), "event2"),
                        json(ids.get(1), "event1"), json(ids.get(0), "event0"));
    }

    @Test
    void relaysInBatchesUntilTheOutboxIsEmpty() {
        OutboxRelay smallBatches = new OutboxRelay(jdbc, transaction, kafkaTemplate, 2, Duration.ofSeconds(10));
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            writer.append(id, TOPIC, "acme/batch", new SampleEvent(id, "b" + i), Instant.now());
        }

        assertThat(smallBatches.relayPending()).isEqualTo(5);
        assertThat(pending()).isZero();
    }

    @Test
    void storesNothingWhenTheSurroundingTransactionRollsBack() {
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            writer.append(eventId, TOPIC, "acme/shop", new SampleEvent(eventId, "lost"), Instant.now());
            throw new IllegalStateException("business write failed");
        })).hasMessage("business write failed");

        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event").query(Integer.class).single()).isZero();
    }

    @Test
    void leavesEventsPendingWhenKafkaIsUnavailable() {
        UUID eventId = UUID.randomUUID();
        writer.append(eventId, TOPIC, "acme/shop", new SampleEvent(eventId, "retry me"), Instant.now());
        DefaultKafkaProducerFactory<String, String> unreachable = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 500,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        try {
            OutboxRelay relayWithoutBroker = new OutboxRelay(
                    jdbc, transaction, new KafkaTemplate<>(unreachable), 100, Duration.ofSeconds(2));

            assertThatThrownBy(relayWithoutBroker::relayPending)
                    .isInstanceOf(OutboxRelay.OutboxRelayException.class);
            assertThat(pending()).isEqualTo(1);
        } finally {
            unreachable.destroy();
        }
    }

    @Test
    void marksAnEventKafkaCanNeverAcceptAsRejectedAndPublishesTheEventsBehindIt() {
        String key = "acme/too-large-" + UUID.randomUUID();
        UUID tooLarge = UUID.randomUUID();
        UUID behind = UUID.randomUUID();
        Instant now = Instant.now();
        // above Kafka's default 1 MB message limit
        writer.append(tooLarge, TOPIC, key, new SampleEvent(tooLarge, "x".repeat(1_200_000)), now);
        writer.append(behind, TOPIC, key, new SampleEvent(behind, "after the large one"), now.plusMillis(1));

        assertThat(relay.relayPending()).isEqualTo(1);

        assertThat(consume(withEventId(behind), 1)).hasSize(1);
        assertThat(pending()).isZero();
        assertThat(jdbc.sql("SELECT rejection FROM outbox_event WHERE id = :id AND rejected_at IS NOT NULL")
                .param("id", tooLarge).query(String.class).single()).startsWith("RecordTooLargeException");
        assertThat(relay.relayPending()).as("a rejected event is not retried").isZero();
    }

    record SampleEvent(UUID id, String text) {
    }

    private static String json(UUID id, String text) {
        return "{\"id\":\"" + id + "\",\"text\":\"" + text + "\"}";
    }

    private int pending() {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NULL AND rejected_at IS NULL")
                .query(Integer.class).single();
    }

    private static Predicate<ConsumerRecord<String, String>> withEventId(UUID eventId) {
        return record -> record.value().contains(eventId.toString());
    }

    private List<ConsumerRecord<String, String>> consume(Predicate<ConsumerRecord<String, String>> filter,
            int expected) {
        return consume(filter, expected, Duration.ofSeconds(10));
    }

    /** Reads the topic from the beginning with a fresh consumer group until enough matching records arrived. */
    private List<ConsumerRecord<String, String>> consume(Predicate<ConsumerRecord<String, String>> filter,
            int expected, Duration timeout) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (Consumer<String, String> consumer =
                consumerFactory.createConsumer("it-" + UUID.randomUUID(), null, null, properties)) {
            consumer.subscribe(List.of(TOPIC));
            Instant deadline = Instant.now().plus(timeout);
            while (matching.size() < expected && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                    if (filter.test(record)) {
                        matching.add(record);
                    }
                });
            }
        }
        return matching;
    }
}
