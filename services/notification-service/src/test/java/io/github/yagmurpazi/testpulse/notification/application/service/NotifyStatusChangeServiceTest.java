package io.github.yagmurpazi.testpulse.notification.application.service;

import static io.github.yagmurpazi.testpulse.notification.domain.StatusChanges.NOW;
import static io.github.yagmurpazi.testpulse.notification.domain.StatusChanges.change;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yagmurpazi.testpulse.notification.application.port.in.DeliveryFailedException;
import io.github.yagmurpazi.testpulse.notification.application.port.in.NotificationResult;
import io.github.yagmurpazi.testpulse.notification.application.port.in.NotificationResult.Outcome;
import io.github.yagmurpazi.testpulse.notification.application.port.out.DeliveryLog;
import io.github.yagmurpazi.testpulse.notification.application.port.out.NotificationChannel;
import io.github.yagmurpazi.testpulse.notification.domain.Notification;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationFactory;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationKind;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationPolicy;
import io.github.yagmurpazi.testpulse.notification.domain.StatusChange;
import io.github.yagmurpazi.testpulse.notification.domain.TestStatus;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotifyStatusChangeServiceTest {

    private final RecordingChannel email = new RecordingChannel("email");
    private final RecordingChannel slack = new RecordingChannel("slack");
    private final InMemoryDeliveryLog deliveryLog = new InMemoryDeliveryLog();

    private NotifyStatusChangeService service(NotificationChannel... channels) {
        return new NotifyStatusChangeService(new NotificationPolicy(Duration.ofHours(24)),
                new NotificationFactory(URI.create("http://localhost:3000")), List.of(channels), deliveryLog,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void sendsANotableChangeToEveryChannelAndRecordsEachDelivery() {
        StatusChange change = change(TestStatus.SUSPICIOUS, TestStatus.FLAKY);

        NotificationResult result = service(email, slack).notify(change);

        assertThat(result).isEqualTo(new NotificationResult(Outcome.NOTIFIED, List.of("email", "slack"), List.of()));
        assertThat(email.sent).singleElement().satisfies(notification -> {
            assertThat(notification.kind()).isEqualTo(NotificationKind.BECAME_FLAKY);
            assertThat(notification.change()).isEqualTo(change);
        });
        assertThat(slack.sent).hasSize(1);
        assertThat(deliveryLog.deliveries).containsOnly(
                Map.entry(change.eventId() + "/email", NOW), Map.entry(change.eventId() + "/slack", NOW));
    }

    @Test
    void sendsNothingForAChangeThePolicyIgnores() {
        NotificationResult result = service(email).notify(change(TestStatus.STABLE, TestStatus.SUSPICIOUS));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_NOTABLE);
        assertThat(email.sent).isEmpty();
        assertThat(deliveryLog.deliveries).isEmpty();
    }

    @Test
    void sendsNothingForAStaleChange() {
        Instant twoDaysAgo = NOW.minus(Duration.ofDays(2));

        NotificationResult result = service(email).notify(change(TestStatus.SUSPICIOUS, TestStatus.FLAKY, twoDaysAgo));

        assertThat(result.outcome()).isEqualTo(Outcome.STALE);
        assertThat(email.sent).isEmpty();
    }

    @Test
    void skipsChannelsThatAlreadyDeliveredARedeliveredChange() {
        StatusChange change = change(TestStatus.SUSPICIOUS, TestStatus.FLAKY);
        service(email, slack).notify(change);

        NotificationResult again = service(email, slack).notify(change);

        assertThat(again).isEqualTo(new NotificationResult(Outcome.NOTIFIED, List.of(), List.of("email", "slack")));
        assertThat(email.sent).hasSize(1);
        assertThat(slack.sent).hasSize(1);
    }

    @Test
    void aFailingChannelDoesNotStopTheOthersAndOnlyItIsRetried() {
        FlakyChannel failingOnce = new FlakyChannel("slack", 1);
        StatusChange change = change(TestStatus.FLAKY, TestStatus.QUARANTINED);

        assertThatThrownBy(() -> service(failingOnce, email).notify(change))
                .isInstanceOfSatisfying(DeliveryFailedException.class, e -> {
                    assertThat(e.failedChannels()).containsExactly("slack");
                    assertThat(e).hasMessageContaining(change.eventId().toString())
                            .hasMessageContaining("webhook down");
                    assertThat(e.getSuppressed()).hasSize(1);
                });
        assertThat(email.sent).hasSize(1);
        assertThat(deliveryLog.deliveries).containsOnlyKeys(change.eventId() + "/email");

        NotificationResult retry = service(failingOnce, email).notify(change);

        assertThat(retry).isEqualTo(new NotificationResult(Outcome.NOTIFIED, List.of("slack"), List.of("email")));
        assertThat(email.sent).as("email is not sent twice").hasSize(1);
        assertThat(failingOnce.attempts).isEqualTo(2);
    }

    @Test
    void reportsEveryFailedChannel() {
        StatusChange change = change(TestStatus.SUSPICIOUS, TestStatus.FLAKY);

        assertThatThrownBy(() -> service(new FlakyChannel("email", 5), new FlakyChannel("slack", 5)).notify(change))
                .isInstanceOfSatisfying(DeliveryFailedException.class, e -> {
                    assertThat(e.failedChannels()).containsExactly("email", "slack");
                    assertThat(e).hasMessageEndingWith("email: webhook down; slack: webhook down");
                });
        assertThat(deliveryLog.deliveries).isEmpty();
    }

    @Test
    void notifiesWithoutChannelsAsAnEmptyDelivery() {
        NotificationResult result = service().notify(change(TestStatus.SUSPICIOUS, TestStatus.FLAKY));

        assertThat(result).isEqualTo(new NotificationResult(Outcome.NOTIFIED, List.of(), List.of()));
    }

    private static class RecordingChannel implements NotificationChannel {

        final List<Notification> sent = new ArrayList<>();
        private final String name;

        RecordingChannel(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void send(Notification notification) {
            sent.add(notification);
        }
    }

    /** Fails the first {@code failures} attempts. */
    private static final class FlakyChannel extends RecordingChannel {

        private final int failures;
        int attempts;

        FlakyChannel(String name, int failures) {
            super(name);
            this.failures = failures;
        }

        @Override
        public void send(Notification notification) {
            attempts++;
            if (attempts <= failures) {
                throw new IllegalStateException("webhook down");
            }
            super.send(notification);
        }
    }

    private static final class InMemoryDeliveryLog implements DeliveryLog {

        final Map<String, Instant> deliveries = new HashMap<>();

        @Override
        public boolean isDelivered(UUID eventId, String channel) {
            return deliveries.containsKey(eventId + "/" + channel);
        }

        @Override
        public void recordDelivered(UUID eventId, String channel, Instant deliveredAt) {
            deliveries.putIfAbsent(eventId + "/" + channel, deliveredAt);
        }
    }
}
