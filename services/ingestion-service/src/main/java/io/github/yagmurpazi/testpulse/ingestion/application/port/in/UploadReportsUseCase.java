package io.github.yagmurpazi.testpulse.ingestion.application.port.in;

import io.github.yagmurpazi.testpulse.ingestion.application.port.out.InvalidReportException;

/** Ingests a CI run from its raw JUnit XML report files. */
public interface UploadReportsUseCase {

    /**
     * Parses the reports and ingests them as one run.
     *
     * @throws IllegalArgumentException if the metadata is invalid or no report was given
     * @throws InvalidReportException   if a report cannot be read
     */
    IngestionResult upload(UploadReportsCommand command);
}
