package io.github.yagmurpazi.testpulse.analysis.application.port.in.query;

import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read side for the dashboard (CQRS-lite). Answered directly from the database, without loading domain objects:
 * queries need shapes the domain does not have, and they change nothing.
 */
public interface TestQueries {

    List<RepositorySummary> repositories();

    /** Tests of a repository, most severe status first; all statuses when {@code status} is empty. */
    List<TestSummary> tests(String repository, Optional<TestStatus> status);

    Optional<TestDetails> test(UUID testId, int latestExecutions);

    /** Daily execution counts of a repository since the given moment, oldest day first. */
    List<DailyTrend> trend(String repository, Instant since);
}
