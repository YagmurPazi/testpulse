package io.github.yagmurpazi.testpulse.notification.application.port.in;

import java.util.List;
import java.util.Objects;

/**
 * What happened to a status change.
 *
 * @param delivered        channels that delivered it now
 * @param alreadyDelivered channels that had delivered it before (a redelivered event)
 */
public record NotificationResult(Outcome outcome, List<String> delivered, List<String> alreadyDelivered) {

    public enum Outcome {
        /** The policy does not notify this kind of change. */
        NOT_NOTABLE,
        /** The change is older than the maximum age. */
        STALE,
        /** Every channel has delivered the change. */
        NOTIFIED
    }

    public NotificationResult {
        Objects.requireNonNull(outcome, "outcome");
        delivered = List.copyOf(delivered);
        alreadyDelivered = List.copyOf(alreadyDelivered);
    }

    public static NotificationResult notNotable() {
        return new NotificationResult(Outcome.NOT_NOTABLE, List.of(), List.of());
    }

    public static NotificationResult stale() {
        return new NotificationResult(Outcome.STALE, List.of(), List.of());
    }

    public static NotificationResult notified(List<String> delivered, List<String> alreadyDelivered) {
        return new NotificationResult(Outcome.NOTIFIED, delivered, alreadyDelivered);
    }
}
