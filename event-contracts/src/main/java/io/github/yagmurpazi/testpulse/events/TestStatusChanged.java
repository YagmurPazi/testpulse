package io.github.yagmurpazi.testpulse.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Published by analysis-service whenever a test changes its {@link TestStatus}, on topic
 * {@link Topics#TEST_STATUS_CHANGES}.
 *
 * <p>Kafka record key: {@link #repository()}, so the changes of one repository are consumed in order.
 *
 * <p>Delivery is at least once: consumers must deduplicate on {@link #eventId()}.
 *
 * @param eventId         unique per event; the idempotency key for consumers
 * @param schemaVersion   version of this contract, see {@link #SCHEMA_VERSION}
 * @param occurredAt      when the status changed
 * @param testId          TestPulse identifier of the test
 * @param repository      repository in {@code owner/name} form
 * @param className       fully qualified test class name
 * @param testName        test method or display name
 * @param previousStatus  status before the change
 * @param newStatus       status after the change
 * @param reason          human-readable explanation, e.g. which evidence triggered the change
 * @param triggeringRunId run whose results caused the change; {@code null} for manual changes such as a quarantine
 */
public record TestStatusChanged(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID testId,
        String repository,
        String className,
        String testName,
        TestStatus previousStatus,
        TestStatus newStatus,
        String reason,
        UUID triggeringRunId) {

    /** Current contract version; evolution rules as in ADR-0005. */
    public static final int SCHEMA_VERSION = 1;

    public TestStatusChanged {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(testId, "testId");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(testName, "testName");
        Objects.requireNonNull(previousStatus, "previousStatus");
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(reason, "reason");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (previousStatus == newStatus) {
            throw new IllegalArgumentException("a status change needs two different statuses");
        }
    }
}
