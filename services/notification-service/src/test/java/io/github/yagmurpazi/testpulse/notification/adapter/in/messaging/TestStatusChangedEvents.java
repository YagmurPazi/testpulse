package io.github.yagmurpazi.testpulse.notification.adapter.in.messaging;

import io.github.yagmurpazi.testpulse.events.TestStatus;
import io.github.yagmurpazi.testpulse.events.TestStatusChanged;
import java.time.Instant;
import java.util.UUID;

/** Test data. */
public final class TestStatusChangedEvents {

    private TestStatusChangedEvents() {
    }

    public static TestStatusChanged event(String repository, String testName, TestStatus from, TestStatus to) {
        return new TestStatusChanged(UUID.randomUUID(), TestStatusChanged.SCHEMA_VERSION, Instant.now(),
                UUID.randomUUID(), repository, "io.example.checkout.CheckoutServiceTest", testName, from, to,
                "Passed and failed on the same commit 9f2c4e1",
                to == TestStatus.QUARANTINED ? null : UUID.randomUUID());
    }
}
