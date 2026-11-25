package io.github.yagmurpazi.testpulse.notification.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Which channel has delivered which status change: the idempotency record of the notifications. */
public interface DeliveryLog {

    boolean isDelivered(UUID eventId, String channel);

    /** Records a delivery; recording the same delivery twice has no effect. */
    void recordDelivered(UUID eventId, String channel, Instant deliveredAt);
}
