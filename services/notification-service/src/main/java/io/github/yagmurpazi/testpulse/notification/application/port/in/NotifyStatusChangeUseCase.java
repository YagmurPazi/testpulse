package io.github.yagmurpazi.testpulse.notification.application.port.in;

import io.github.yagmurpazi.testpulse.notification.domain.StatusChange;

/** Tells the team about a test's status change through every configured channel. */
public interface NotifyStatusChangeUseCase {

    /**
     * Notifies the change unless the policy filters it out. Idempotent per channel: a channel that already
     * delivered this change is skipped, so a retry only repeats the channels that failed.
     *
     * @throws DeliveryFailedException if at least one channel failed; the others are recorded as delivered
     */
    NotificationResult notify(StatusChange change);
}
