package io.github.yagmurpazi.testpulse.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A test's status changed. The input of every notification.
 *
 * @param eventId    identifies the change; the idempotency key of its deliveries
 * @param manual     {@code true} for a team decision (quarantine, release), {@code false} for an evaluation
 */
public record StatusChange(
        UUID eventId,
        UUID testId,
        String repository,
        String className,
        String testName,
        TestStatus from,
        TestStatus to,
        String reason,
        Instant occurredAt,
        boolean manual) {

    public StatusChange {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(testId, "testId");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(testName, "testName");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (from == to) {
            throw new IllegalArgumentException("a status change needs two different statuses");
        }
    }

    /** "CheckoutServiceTest.appliesDiscount": short enough for a subject line, unique enough to recognise. */
    public String testLabel() {
        return className.substring(className.lastIndexOf('.') + 1) + "." + testName;
    }
}
