package io.github.yagmurpazi.testpulse.notification.application.service;

import io.github.yagmurpazi.testpulse.notification.application.port.in.DeliveryFailedException;
import io.github.yagmurpazi.testpulse.notification.application.port.in.NotificationResult;
import io.github.yagmurpazi.testpulse.notification.application.port.in.NotifyStatusChangeUseCase;
import io.github.yagmurpazi.testpulse.notification.application.port.out.DeliveryLog;
import io.github.yagmurpazi.testpulse.notification.application.port.out.NotificationChannel;
import io.github.yagmurpazi.testpulse.notification.domain.Notification;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationFactory;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationKind;
import io.github.yagmurpazi.testpulse.notification.domain.NotificationPolicy;
import io.github.yagmurpazi.testpulse.notification.domain.StatusChange;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Sends a notable status change to every channel. Each delivery is recorded separately, after the channel
 * accepted it, so one failing channel neither blocks the others nor makes them send twice on a retry.
 *
 * <p>A crash between sending and recording sends that channel's message again on redelivery: delivery is at
 * least once, as an e-mail or a Slack message cannot take part in a database transaction.
 */
public final class NotifyStatusChangeService implements NotifyStatusChangeUseCase {

    private final NotificationPolicy policy;
    private final NotificationFactory factory;
    private final List<NotificationChannel> channels;
    private final DeliveryLog deliveryLog;
    private final Clock clock;

    public NotifyStatusChangeService(NotificationPolicy policy, NotificationFactory factory,
            List<NotificationChannel> channels, DeliveryLog deliveryLog, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.channels = List.copyOf(channels);
        this.deliveryLog = Objects.requireNonNull(deliveryLog, "deliveryLog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public NotificationResult notify(StatusChange change) {
        Optional<NotificationKind> kind = policy.kindOf(change);
        if (kind.isEmpty()) {
            return NotificationResult.notNotable();
        }
        if (policy.isStale(change, clock.instant())) {
            return NotificationResult.stale();
        }
        Notification notification = factory.create(change, kind.get());

        List<String> delivered = new ArrayList<>();
        List<String> alreadyDelivered = new ArrayList<>();
        Map<String, RuntimeException> failures = new LinkedHashMap<>();
        for (NotificationChannel channel : channels) {
            if (deliveryLog.isDelivered(change.eventId(), channel.name())) {
                alreadyDelivered.add(channel.name());
                continue;
            }
            try {
                channel.send(notification);
            } catch (RuntimeException e) {
                failures.put(channel.name(), e);
                continue;
            }
            deliveryLog.recordDelivered(change.eventId(), channel.name(), clock.instant());
            delivered.add(channel.name());
        }
        if (!failures.isEmpty()) {
            throw new DeliveryFailedException(change.eventId(), failures);
        }
        return NotificationResult.notified(delivered, alreadyDelivered);
    }
}
