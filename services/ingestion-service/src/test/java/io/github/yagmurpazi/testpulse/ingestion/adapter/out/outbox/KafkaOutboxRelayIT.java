package io.github.yagmurpazi.testpulse.ingestion.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import io.github.yagmurpazi.testpulse.events.Topics;
import io.github.yagmurpazi.testpulse.ingestion.IntegrationTest;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunUseCase;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestData;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Outbox to Kafka against a real broker: what is published, how it is keyed, and what happens on failure. */
class KafkaOutboxRelayIT extends IntegrationTest {

    @Autowired
    private IngestTestRunUseCase ingestTestRun;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void createsTestRunsTopicWithThreePartitions() {
        TopicDescription topic = kafkaAdmin.describeTopics(Topics.TEST_RUNS).get(Topics.TEST_RUNS);

        assertThat(topic.partitions()).hasSize(3);
    }

    @Test
    void publishesPendingEventKeyedByRepositoryWithEventIdHeader() {
        TestRun run = ingest(TestData.IDENTITY);

        assertThat(relay.relayPending()).isEqualTo(1);

        ConsumerRecord<String, String> record = consumeSingle(forRun(run));
        assertThat(record.key()).isEqualTo("YagmurPazi/testpulse");
        TestRunIngested event = jsonMapper.readValue(record.value(), TestRunIngested.class);
        assertThat(event.runId()).isEqualTo(run.id());
        assertThat(event.results()).hasSize(2);
        assertThat(new String(record.headers().lastHeader(OutboxRelay.EVENT_ID_HEADER).value(),
                StandardCharsets.UTF_8)).isEqualTo(event.eventId().toString());
        assertThat(count("outbox_event WHERE published_at IS NOT NULL")).isEqualTo(1);
    }

    @Test
    void publishesEachEventOnlyOnce() {
        TestRun run = ingest(TestData.IDENTITY);

        relay.relayPending();

        assertThat(relay.relayPending()).isZero();
        assertThat(consume(forRun(run), Duration.ofSeconds(3))).hasSize(1);
    }

    @Test
    void keepsRunsOfOneRepositoryInOnePartitionInOrder() {
        String repository = "acme/ordered-" + UUID.randomUUID();
        List<TestRun> runs = new ArrayList<>();
        for (int attempt = 1; attempt <= 3; attempt++) {
            runs.add(ingest(new RunIdentity(repository, "1", attempt)));
        }

        relay.relayPending();

        List<ConsumerRecord<String, String>> records = consume(
                record -> runs.stream().anyMatch(run -> forRun(run).test(record)), Duration.ofSeconds(10), 3);
        assertThat(records).extracting(ConsumerRecord::partition).containsOnly(records.getFirst().partition());
        assertThat(records).extracting(record -> jsonMapper.readValue(record.value(), TestRunIngested.class).attempt())
                .containsExactly(1, 2, 3);
    }

    @Test
    void leavesEventsPendingWhenKafkaIsUnavailable() {
        ingest(TestData.IDENTITY);
        DefaultKafkaProducerFactory<String, String> unreachable = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 500,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        OutboxRelay relayWithoutBroker = new OutboxRelay(
                jdbc, transaction, new KafkaTemplate<>(unreachable), 100, Duration.ofSeconds(2));
        try {
            assertThatThrownBy(relayWithoutBroker::relayPending)
                    .isInstanceOf(OutboxRelay.OutboxRelayException.class);

            assertThat(count("outbox_event WHERE published_at IS NULL")).isEqualTo(1);
        } finally {
            unreachable.destroy();
        }
    }

    private TestRun ingest(RunIdentity identity) {
        return ingestTestRun.ingest(new IngestTestRunCommand(identity, TestData.COMMIT, "main",
                List.of(TestData.passed("a"), TestData.failed("b")))).run();
    }

    private Predicate<ConsumerRecord<String, String>> forRun(TestRun run) {
        return record -> record.value().contains(run.id().toString());
    }

    private ConsumerRecord<String, String> consumeSingle(Predicate<ConsumerRecord<String, String>> filter) {
        List<ConsumerRecord<String, String>> records = consume(filter, Duration.ofSeconds(10), 1);
        assertThat(records).as("records matching the filter").hasSize(1);
        return records.getFirst();
    }

    private List<ConsumerRecord<String, String>> consume(Predicate<ConsumerRecord<String, String>> filter,
            Duration timeout) {
        return consume(filter, timeout, Integer.MAX_VALUE);
    }

    /** Reads the topic from the beginning with a fresh consumer group until enough matching records arrived. */
    private List<ConsumerRecord<String, String>> consume(Predicate<ConsumerRecord<String, String>> filter,
            Duration timeout, int expected) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (Consumer<String, String> consumer =
                consumerFactory.createConsumer("it-" + UUID.randomUUID(), null, null, properties)) {
            consumer.subscribe(List.of(Topics.TEST_RUNS));
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
