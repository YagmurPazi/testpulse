package io.github.yagmurpazi.testpulse.notification.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides which status changes reach people. Every message has to be worth reading, or people start ignoring all
 * of them, which is the problem flaky tests cause in the first place:
 *
 * <ul>
 *   <li>Changes that need attention or record a decision are notified: a test became flaky, was quarantined,
 *       released, or a flaky test recovered.</li>
 *   <li>Changes into and out of <i>suspicious</i> are not: suspicious means "watching", and most suspicious tests
 *       return to stable without anyone acting.</li>
 *   <li>Old changes are not: on its first start the service reads the whole topic, and a burst of messages about
 *       last month's changes would be noise.</li>
 * </ul>
 */
public final class NotificationPolicy {

    private final Duration maxAge;

    public NotificationPolicy(Duration maxAge) {
        Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("maxAge must be positive");
        }
        this.maxAge = maxAge;
    }

    /** The kind of notification the change deserves, if any, regardless of its age. */
    public Optional<NotificationKind> kindOf(StatusChange change) {
        if (change.to() == TestStatus.FLAKY) {
            return Optional.of(NotificationKind.BECAME_FLAKY);
        }
        if (change.to() == TestStatus.QUARANTINED) {
            return Optional.of(NotificationKind.QUARANTINED);
        }
        if (change.from() == TestStatus.QUARANTINED) {
            return Optional.of(NotificationKind.RELEASED);
        }
        if (change.from() == TestStatus.FLAKY && change.to() == TestStatus.STABLE) {
            return Optional.of(NotificationKind.RECOVERED);
        }
        return Optional.empty();
    }

    /** {@code true} if the change happened too long ago to be news. */
    public boolean isStale(StatusChange change, Instant now) {
        return change.occurredAt().isBefore(now.minus(maxAge));
    }
}
