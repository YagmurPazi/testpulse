package io.github.yagmurpazi.testpulse.notification.domain;

import java.time.Instant;
import java.util.UUID;

/** Test data. */
public final class StatusChanges {

    public static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    public static final UUID TEST_ID = UUID.fromString("ce1cbd14-a6b1-4100-8d48-2441ee64eb6f");

    private StatusChanges() {
    }

    public static StatusChange change(TestStatus from, TestStatus to) {
        return change(from, to, NOW);
    }

    public static StatusChange change(TestStatus from, TestStatus to, Instant occurredAt) {
        return new StatusChange(UUID.randomUUID(), TEST_ID, "acme/checkout",
                "io.example.checkout.CheckoutServiceTest", "appliesDiscount", from, to,
                "Passed and failed on the same commit 9f2c4e1", occurredAt, to == TestStatus.QUARANTINED);
    }
}
