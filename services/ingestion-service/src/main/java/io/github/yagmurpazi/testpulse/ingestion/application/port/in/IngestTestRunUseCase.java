package io.github.yagmurpazi.testpulse.ingestion.application.port.in;

/** Stores a CI run and announces it to the rest of the system. */
public interface IngestTestRunUseCase {

    /**
     * Stores the run unless a run with the same identity exists. Idempotent: ingesting the same identity again
     * returns the stored run and publishes no further event.
     */
    IngestionResult ingest(IngestTestRunCommand command);
}
