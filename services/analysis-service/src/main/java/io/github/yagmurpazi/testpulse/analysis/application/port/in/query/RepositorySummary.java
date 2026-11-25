package io.github.yagmurpazi.testpulse.analysis.application.port.in.query;

import java.time.Instant;

/** A repository with test counts per status. */
public record RepositorySummary(String repository, int tests, int stable, int suspicious, int flaky,
        int quarantined, Instant lastRunAt) {
}
