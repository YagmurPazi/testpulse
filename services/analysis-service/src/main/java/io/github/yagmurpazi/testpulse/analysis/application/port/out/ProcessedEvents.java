package io.github.yagmurpazi.testpulse.analysis.application.port.out;

import java.util.UUID;

/** Remembers which events were processed (idempotent consumer, also known as inbox). */
public interface ProcessedEvents {

    /**
     * Marks the event as processed, in the current transaction.
     *
     * @return {@code false} if it was processed before
     */
    boolean markProcessed(UUID eventId);
}
