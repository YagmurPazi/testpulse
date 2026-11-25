package io.github.yagmurpazi.testpulse.notification.application.port.out;

import io.github.yagmurpazi.testpulse.notification.domain.Notification;

/**
 * A way to reach people (Observer): every configured channel receives every notification and formats it its own
 * way. A new channel is a new implementation; the use case does not change.
 */
public interface NotificationChannel {

    /** Stable name, part of the delivery record: renaming a channel makes it deliver everything again. */
    String name();

    /** Delivers the notification, or throws if it could not. */
    void send(Notification notification);
}
