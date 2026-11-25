package io.github.yagmurpazi.testpulse.ingestion.application.port.in;

import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.util.Objects;

/**
 * Outcome of an ingestion.
 *
 * @param run     the stored run: the new one, or the one stored earlier with the same identity
 * @param created {@code true} if this call stored the run, {@code false} if it was already known
 */
public record IngestionResult(TestRun run, boolean created) {

    public IngestionResult {
        Objects.requireNonNull(run, "run");
    }

    public static IngestionResult created(TestRun run) {
        return new IngestionResult(run, true);
    }

    public static IngestionResult alreadyIngested(TestRun run) {
        return new IngestionResult(run, false);
    }
}
