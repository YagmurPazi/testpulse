package io.github.yagmurpazi.testpulse.ingestion.adapter.out.persistence;

import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunRepository;
import io.github.yagmurpazi.testpulse.ingestion.domain.CommitSha;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestOutcome;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL storage of test runs with plain SQL.
 *
 * <p>Idempotency relies on {@code INSERT ... ON CONFLICT DO NOTHING} against the unique run identity: under
 * concurrency PostgreSQL makes the second insert wait for the first transaction and then skip, so exactly one caller
 * stores the run.
 */
@Repository
class JdbcTestRunRepository implements TestRunRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    JdbcTestRunRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TestRun> findByIdentity(RunIdentity identity) {
        return jdbc.sql("""
                        SELECT id, commit_sha, branch, ingested_at
                          FROM test_run
                         WHERE repository = :repository AND external_run_id = :externalRunId AND attempt = :attempt
                        """)
                .param("repository", identity.repository())
                .param("externalRunId", identity.externalRunId())
                .param("attempt", identity.attempt())
                .query((rs, row) -> new RunRow(
                        rs.getObject("id", UUID.class),
                        new CommitSha(rs.getString("commit_sha")),
                        rs.getString("branch"),
                        rs.getObject("ingested_at", OffsetDateTime.class)))
                .optional()
                .map(row -> new TestRun(row.id(), identity, row.commitSha(), row.branch(),
                        row.ingestedAt().toInstant(), findResults(row.id())));
    }

    @Override
    public boolean saveIfAbsent(TestRun run) {
        int inserted = jdbc.sql("""
                        INSERT INTO test_run
                            (id, repository, external_run_id, attempt, commit_sha, branch, ingested_at)
                        VALUES (:id, :repository, :externalRunId, :attempt, :commitSha, :branch, :ingestedAt)
                        ON CONFLICT ON CONSTRAINT uq_test_run_identity DO NOTHING
                        """)
                .param("id", run.id())
                .param("repository", run.identity().repository())
                .param("externalRunId", run.identity().externalRunId())
                .param("attempt", run.identity().attempt())
                .param("commitSha", run.commitSha().value())
                .param("branch", run.branch())
                .param("ingestedAt", OffsetDateTime.ofInstant(run.ingestedAt(), ZoneOffset.UTC))
                .update();
        if (inserted == 0) {
            return false;
        }
        insertResults(run);
        return true;
    }

    private void insertResults(TestRun run) {
        List<TestCaseResult> results = run.results();
        jdbcTemplate.batchUpdate("""
                        INSERT INTO test_case_result
                            (run_id, position, class_name, test_name, outcome, duration_ms, rerun_failures,
                             failure_message)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                indexesOf(results), BATCH_SIZE, (statement, position) -> {
                    TestCaseResult result = results.get(position);
                    statement.setObject(1, run.id());
                    statement.setInt(2, position);
                    statement.setString(3, result.className());
                    statement.setString(4, result.testName());
                    statement.setString(5, result.outcome().name());
                    statement.setLong(6, result.duration().toMillis());
                    statement.setInt(7, result.rerunFailures());
                    if (result.failureMessage() == null) {
                        statement.setNull(8, Types.VARCHAR);
                    } else {
                        statement.setString(8, result.failureMessage());
                    }
                });
    }

    private List<TestCaseResult> findResults(UUID runId) {
        return jdbc.sql("""
                        SELECT class_name, test_name, outcome, duration_ms, rerun_failures, failure_message
                          FROM test_case_result
                         WHERE run_id = :runId
                         ORDER BY position
                        """)
                .param("runId", runId)
                .query(JdbcTestRunRepository::toResult)
                .list();
    }

    private static TestCaseResult toResult(ResultSet rs, int row) throws SQLException {
        return new TestCaseResult(
                rs.getString("class_name"),
                rs.getString("test_name"),
                TestOutcome.valueOf(rs.getString("outcome")),
                Duration.ofMillis(rs.getLong("duration_ms")),
                rs.getInt("rerun_failures"),
                rs.getString("failure_message"));
    }

    private static List<Integer> indexesOf(List<?> list) {
        return IntStream.range(0, list.size()).boxed().toList();
    }

    private record RunRow(UUID id, CommitSha commitSha, String branch, OffsetDateTime ingestedAt) {
    }
}
