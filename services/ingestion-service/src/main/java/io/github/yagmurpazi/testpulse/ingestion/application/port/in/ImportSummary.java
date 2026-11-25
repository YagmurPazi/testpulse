package io.github.yagmurpazi.testpulse.ingestion.application.port.in;

import java.util.List;
import java.util.Objects;

/**
 * What an import did with the recent runs of a repository.
 *
 * @param imported         runs ingested now
 * @param alreadyIngested  runs skipped because they were ingested before
 * @param withoutReports   runs without (unexpired) test report artifacts
 * @param failures         runs that could not be imported, with the reason; retried on the next import
 */
public record ImportSummary(String repository, int imported, int alreadyIngested, int withoutReports,
        List<String> failures) {

    public ImportSummary {
        Objects.requireNonNull(repository, "repository");
        failures = List.copyOf(failures);
    }
}
