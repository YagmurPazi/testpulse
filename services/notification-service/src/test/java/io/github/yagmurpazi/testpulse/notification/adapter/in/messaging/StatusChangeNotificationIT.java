package io.github.yagmurpazi.testpulse.notification.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.yagmurpazi.testpulse.events.TestStatus;
import io.github.yagmurpazi.testpulse.events.TestStatusChanged;
import io.github.yagmurpazi.testpulse.events.Topics;
import io.github.yagmurpazi.testpulse.notification.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Events through a real broker to a real SMTP server. Records with the same key share a partition and are consumed
 * in order, so once a later record's e-mail arrived, every earlier record has been handled too.
 */
class StatusChangeNotificationIT extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void mailsAFlakyTest() {
        String repository = uniqueRepository();

        send(TestStatusChangedEvents.event(repository, "appliesDiscount", TestStatus.SUSPICIOUS, TestStatus.FLAKY));

        awaitSubjects("[TestPulse] Flaky test: CheckoutServiceTest.appliesDiscount in " + repository);
        assertThat(count("delivered_notification WHERE channel = 'email'")).isEqualTo(1);
    }

    @Test
    void mailsARedeliveredChangeOnlyOnce() {
        String repository = uniqueRepository();
        TestStatusChanged event = TestStatusChangedEvents.event(repository, "appliesDiscount",
                TestStatus.FLAKY, TestStatus.QUARANTINED);

        send(event);
        send(event);
        send(TestStatusChangedEvents.event(repository, "marker", TestStatus.SUSPICIOUS, TestStatus.FLAKY));

        awaitSubjects("[TestPulse] Quarantined: CheckoutServiceTest.appliesDiscount in " + repository,
                "[TestPulse] Flaky test: CheckoutServiceTest.marker in " + repository);
    }

    @Test
    void mailsNothingForChangesThePolicyIgnores() {
        String repository = uniqueRepository();

        send(TestStatusChangedEvents.event(repository, "suspicious", TestStatus.STABLE, TestStatus.SUSPICIOUS));
        TestStatusChanged old = TestStatusChangedEvents.event(repository, "old", TestStatus.SUSPICIOUS,
                TestStatus.FLAKY);
        send(new TestStatusChanged(old.eventId(), old.schemaVersion(), Instant.now().minus(Duration.ofDays(3)),
                old.testId(), old.repository(), old.className(), old.testName(), old.previousStatus(),
                old.newStatus(), old.reason(), old.triggeringRunId()));
        send(TestStatusChangedEvents.event(repository, "marker", TestStatus.SUSPICIOUS, TestStatus.FLAKY));

        awaitSubjects("[TestPulse] Flaky test: CheckoutServiceTest.marker in " + repository);
    }

    @Test
    void skipsAPoisonMessageWithoutBlockingThePartition() {
        String repository = uniqueRepository();

        kafka.send(Topics.TEST_STATUS_CHANGES, repository, "{ this is not json");
        send(TestStatusChangedEvents.event(repository, "afterThePoison", TestStatus.SUSPICIOUS, TestStatus.FLAKY));

        awaitSubjects("[TestPulse] Flaky test: CheckoutServiceTest.afterThePoison in " + repository);
    }

    private void send(TestStatusChanged event) {
        kafka.send(Topics.TEST_STATUS_CHANGES, event.repository(), jsonMapper.writeValueAsString(event));
    }

    /** Waits until exactly these e-mails have arrived, in any order. */
    private void awaitSubjects(String... subjects) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(mailpit.subjects()).containsExactlyInAnyOrder(subjects));
    }

    private static String uniqueRepository() {
        return "it/repo-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
