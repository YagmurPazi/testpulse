package io.github.yagmurpazi.testpulse.analysis.adapter.out.persistence;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.DailyTrend;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.RepositorySummary;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.TestDetails;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.TestQueries;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.TestSummary;
import io.github.yagmurpazi.testpulse.analysis.domain.Outcome;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL implementation of the dashboard queries. */
@Repository
class JdbcTestQueries implements TestQueries {

    private final JdbcClient jdbc;

    JdbcTestQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RepositorySummary> repositories() {
        return jdbc.sql("""
                        SELECT t.repository,
                               count(*)                                         AS tests,
                               count(*) FILTER (WHERE t.status = 'STABLE')      AS stable,
                               count(*) FILTER (WHERE t.status = 'SUSPICIOUS')  AS suspicious,
                               count(*) FILTER (WHERE t.status = 'FLAKY')       AS flaky,
                               count(*) FILTER (WHERE t.status = 'QUARANTINED') AS quarantined,
                               max(latest.executed_at)                          AS last_run_at
                          FROM monitored_test t
                          LEFT JOIN LATERAL (SELECT executed_at FROM test_execution e
                                              WHERE e.test_id = t.id
                                              ORDER BY executed_at DESC LIMIT 1) latest ON true
                         GROUP BY t.repository
                         ORDER BY t.repository
                        """)
                .query((rs, row) -> new RepositorySummary(rs.getString("repository"), rs.getInt("tests"),
                        rs.getInt("stable"), rs.getInt("suspicious"), rs.getInt("flaky"), rs.getInt("quarantined"),
                        instant(rs, "last_run_at")))
                .list();
    }

    @Override
    public List<TestSummary> tests(String repository, Optional<TestStatus> status) {
        return jdbc.sql("""
                        SELECT t.id, t.class_name, t.test_name, t.status,
                               coalesce(t.last_changed_at, t.first_seen_at) AS status_since,
                               (SELECT c.reason FROM status_change c WHERE c.test_id = t.id
                                 ORDER BY c.changed_at DESC LIMIT 1) AS last_reason,
                               (SELECT count(*) FROM test_execution e WHERE e.test_id = t.id) AS executions,
                               (SELECT count(*) FROM test_execution e WHERE e.test_id = t.id
                                   AND e.outcome IN ('FAILED', 'ERROR')) AS failures
                          FROM monitored_test t
                         WHERE t.repository = :repository
                           AND (CAST(:status AS varchar) IS NULL OR t.status = :status)
                         ORDER BY CASE t.status WHEN 'FLAKY' THEN 0 WHEN 'QUARANTINED' THEN 1
                                                WHEN 'SUSPICIOUS' THEN 2 ELSE 3 END,
                                  t.class_name, t.test_name
                        """)
                .param("repository", repository)
                .param("status", status.map(Enum::name).orElse(null))
                .query((rs, row) -> new TestSummary(rs.getObject("id", UUID.class), rs.getString("class_name"),
                        rs.getString("test_name"), TestStatus.valueOf(rs.getString("status")),
                        instant(rs, "status_since"), rs.getString("last_reason"), rs.getInt("executions"),
                        rs.getInt("failures")))
                .list();
    }

    @Override
    public Optional<TestDetails> test(UUID testId, int latestExecutions) {
        return jdbc.sql("""
                        SELECT id, repository, class_name, test_name, status,
                               coalesce(last_changed_at, first_seen_at) AS status_since
                          FROM monitored_test WHERE id = :id
                        """)
                .param("id", testId)
                .query((rs, row) -> new TestDetails(testId, rs.getString("repository"), rs.getString("class_name"),
                        rs.getString("test_name"), TestStatus.valueOf(rs.getString("status")),
                        instant(rs, "status_since"), executions(testId, latestExecutions), changes(testId)))
                .optional();
    }

    @Override
    public List<DailyTrend> trend(String repository, Instant since) {
        return jdbc.sql("""
                        SELECT CAST(e.executed_at AT TIME ZONE 'UTC' AS date)                     AS day,
                               count(DISTINCT e.run_id)                                           AS runs,
                               count(*)                                                           AS executions,
                               count(*) FILTER (WHERE e.outcome IN ('FAILED', 'ERROR'))           AS failures,
                               count(*) FILTER (WHERE e.outcome = 'PASSED' AND e.rerun_failures > 0)
                                                                                                  AS passed_on_rerun
                          FROM test_execution e
                          JOIN monitored_test t ON t.id = e.test_id
                         WHERE t.repository = :repository AND e.executed_at >= :since
                         GROUP BY day
                         ORDER BY day
                        """)
                .param("repository", repository)
                .param("since", JdbcMonitoredTestRepository.utc(since))
                .query((rs, row) -> new DailyTrend(rs.getObject("day", LocalDate.class), rs.getInt("runs"),
                        rs.getInt("executions"), rs.getInt("failures"), rs.getInt("passed_on_rerun")))
                .list();
    }

    private List<TestDetails.Execution> executions(UUID testId, int limit) {
        return jdbc.sql("""
                        SELECT run_id, commit_sha, attempt, outcome, rerun_failures, duration_ms, executed_at
                          FROM test_execution WHERE test_id = :id
                         ORDER BY executed_at DESC LIMIT :limit
                        """)
                .param("id", testId)
                .param("limit", limit)
                .query((rs, row) -> new TestDetails.Execution(rs.getObject("run_id", UUID.class),
                        rs.getString("commit_sha"), rs.getInt("attempt"), Outcome.valueOf(rs.getString("outcome")),
                        rs.getInt("rerun_failures"), rs.getLong("duration_ms"), instant(rs, "executed_at")))
                .list();
    }

    private List<TestDetails.Change> changes(UUID testId) {
        return jdbc.sql("""
                        SELECT from_status, to_status, reason, changed_at, triggering_run_id
                          FROM status_change WHERE test_id = :id ORDER BY changed_at
                        """)
                .param("id", testId)
                .query((rs, row) -> new TestDetails.Change(TestStatus.valueOf(rs.getString("from_status")),
                        TestStatus.valueOf(rs.getString("to_status")), rs.getString("reason"),
                        instant(rs, "changed_at"), rs.getObject("triggering_run_id", UUID.class)))
                .list();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
