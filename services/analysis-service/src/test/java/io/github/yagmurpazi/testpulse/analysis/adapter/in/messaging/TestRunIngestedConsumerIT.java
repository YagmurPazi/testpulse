package io.github.yagmurpazi.testpulse.analysis.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.yagmurpazi.testpulse.analysis.IntegrationTest;
import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import io.github.yagmurpazi.testpulse.events.Topics;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Events through a real broker into the database. Records with the same key share a partition and are consumed in
 * order, so once a later record is processed, every earlier one has been handled too.
 */
class TestRunIngestedConsumerIT extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void processesAPublishedRun() {
        String repository = uniqueRepository();

        send(TestRunIngestedEvents.event(repository, "appliesDiscount", "calculatesTotal"));

        awaitTests(repository, 2);
        assertThat(count("test_execution")).isEqualTo(2);
    }

    @Test
    void processesARedeliveredEventOnlyOnce() {
        String repository = uniqueRepository();
        TestRunIngested event = TestRunIngestedEvents.event(repository, "appliesDiscount");

        send(event);
        send(event);
        send(TestRunIngestedEvents.event(repository, "marker"));

        awaitTests(repository, 2);
        assertThat(count("test_execution")).as("the duplicate added nothing").isEqualTo(2);
        assertThat(count("processed_event")).isEqualTo(2);
    }

    @Test
    void skipsAPoisonMessageWithoutBlockingThePartition() {
        String repository = uniqueRepository();

        kafka.send(Topics.TEST_RUNS, repository, "{ this is not json");
        send(TestRunIngestedEvents.event(repository, "afterThePoison"));

        awaitTests(repository, 1);
    }

    @Test
    void skipsAnEventWithAnUnsupportedSchemaVersion() {
        String repository = uniqueRepository();
        ObjectNode future = jsonMapper.valueToTree(TestRunIngestedEvents.event(repository, "fromTheFuture"));
        future.put("schemaVersion", 2);

        kafka.send(Topics.TEST_RUNS, repository, future.toString());
        send(TestRunIngestedEvents.event(repository, "current"));

        awaitTests(repository, 1);
        assertThat(jdbc.sql("SELECT test_name FROM monitored_test").query(String.class).single())
                .isEqualTo("current");
    }

    private void send(TestRunIngested event) {
        kafka.send(Topics.TEST_RUNS, event.repository(), jsonMapper.writeValueAsString(event));
    }

    private void awaitTests(String repository, int expected) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(
                jdbc.sql("SELECT count(*) FROM monitored_test WHERE repository = :repository")
                        .param("repository", repository).query(Integer.class).single()).isEqualTo(expected));
    }

    private static String uniqueRepository() {
        return "acme/consumer-" + UUID.randomUUID();
    }
}
