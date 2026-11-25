package io.github.yagmurpazi.testpulse.analysis.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A status change that happened; becomes a stored transition and a published event.
 *
 * @param testId          the test
 * @param key             natural identity of the test
 * @param from            status before
 * @param to              status after
 * @param reason          why
 * @param changedAt       when
 * @param triggeringRunId run that caused it, {@code null} for manual changes
 */
public record StatusChange(
        UUID testId,
        TestKey key,
        TestStatus from,
        TestStatus to,
        String reason,
        Instant changedAt,
        UUID triggeringRunId) {

    public StatusChange {
        Objects.requireNonNull(testId, "testId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(changedAt, "changedAt");
        if (from == to) {
            throw new IllegalArgumentException("a status change needs two different statuses");
        }
    }
}
