package io.github.yagmurpazi.testpulse.ingestion.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import io.github.yagmurpazi.testpulse.events.Topics;
import io.github.yagmurpazi.testpulse.ingestion.IntegrationTest;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunUseCase;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestData;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import io.github.yagmurpazi.testpulse.outbox.OutboxRelay;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ingestion's events on the real topic. Generic relay behaviour (ordering, retries, failures) is tested in the
 * outbox module; this test covers what is specific to this service: its topic and its event.
 */
class TestRunEventsKafkaIT extends IntegrationTest {

    @Autowired
    private IngestTestRunUseCase ingestTestRun;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void createsTestRunsTopicWithThreePartitions() {
        TopicDescription topic = kafkaAdmin.describeTopics(Topics.TEST_RUNS).get(Topics.TEST_RUNS);

        assertThat(topic.partitions()).hasSize(3);
    }

    @Test
    void publishesTestRunIngestedKeyedByRepository() {
        TestRun run = ingestTestRun.ingest(new IngestTestRunCommand(TestData.IDENTITY, TestData.COMMIT, "main",
                List.of(TestData.passed("a"), TestData.failed("b")))).run();

        assertThat(relay.relayPending()).isEqualTo(1);

        ConsumerRecord<String, String> record = consumeRecordOf(run);
        assertThat(record.key()).isEqualTo("YagmurPazi/testpulse");
        TestRunIngested event = jsonMapper.readValue(record.value(), TestRunIngested.class);
        assertThat(event.runId()).isEqualTo(run.id());
        assertThat(event.results()).hasSize(2);
        assertThat(record.headers().lastHeader(OutboxRelay.EVENT_ID_HEADER)).isNotNull();
    }

    /** A run of the size of apache/cassandra-java-driver: one event would exceed Kafka's 1 MB message limit. */
    @Test
    void publishesALargeRunAsSeveralMessagesKafkaAccepts() {
        RunIdentity identity = new RunIdentity("apache/cassandra-java-driver", "45650397699", 2);
        TestRun run = ingestTestRun.ingest(new IngestTestRunCommand(identity, TestData.COMMIT, "4.x",
                IntStream.range(0, 6000).mapToObj(i -> TestData.result(
                        "com.datastax.oss.driver.api.core.cql.continuous.reactive.Class" + i / 20,
                        "should_handle_reactive_continuous_paging_with_case_" + i, i % 50 == 0)).toList())).run();

        int published = relay.relayPending();

        assertThat(published).isGreaterThan(1);
        List<TestRunIngested> parts = consumeRecordsOf(run, published).stream()
                .map(record -> jsonMapper.readValue(record.value(), TestRunIngested.class)).toList();
        assertThat(parts).extracting(TestRunIngested::part)
                .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, published).boxed().toList());
        assertThat(parts.stream().mapToInt(part -> part.results().size()).sum()).isEqualTo(6000);
    }

    private ConsumerRecord<String, String> consumeRecordOf(TestRun run) {
        return consumeRecordsOf(run, 1).getFirst();
    }

    private List<ConsumerRecord<String, String>> consumeRecordsOf(TestRun run, int expected) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        Properties properties = new Properties();
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer =
                consumerFactory.createConsumer("it-" + UUID.randomUUID(), null, null, properties)) {
            consumer.subscribe(List.of(Topics.TEST_RUNS));
            Instant deadline = Instant.now().plusSeconds(20);
            while (Instant.now().isBefore(deadline) && found.size() < expected) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    if (record.value().contains(run.id().toString())) {
                        found.add(record);
                    }
                }
            }
        }
        if (found.size() < expected) {
            throw new AssertionError(
                    "expected " + expected + " record(s) for run " + run.id() + ", got " + found.size());
        }
        return found;
    }
}
