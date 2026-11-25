package io.github.yagmurpazi.testpulse.analysis.adapter.out.persistence;

import io.github.yagmurpazi.testpulse.analysis.application.port.out.RecordedExecution;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.TestExecutionRepository;
import io.github.yagmurpazi.testpulse.analysis.domain.Outcome;
import io.github.yagmurpazi.testpulse.analysis.domain.TestExecution;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Execution history in PostgreSQL. */
@Repository
class JdbcTestExecutionRepository implements TestExecutionRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    JdbcTestExecutionRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void appendAll(Collection<RecordedExecution> executions) {
        jdbcTemplate.batchUpdate("""
                        INSERT INTO test_execution (test_id, run_id, commit_sha, attempt, outcome, rerun_failures,
                                                    duration_ms, executed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                List.copyOf(executions), 500, (statement, recorded) -> {
                    TestExecution execution = recorded.execution();
                    statement.setObject(1, recorded.testId());
                    statement.setObject(2, execution.runId());
                    statement.setString(3, execution.commitSha());
                    statement.setInt(4, execution.attempt());
                    statement.setString(5, execution.outcome().name());
                    statement.setInt(6, execution.rerunFailures());
                    statement.setLong(7, recorded.durationMillis());
                    statement.setObject(8, JdbcMonitoredTestRepository.utc(execution.executedAt()));
                });
    }

    /**
     * One query for all tests of a run. {@code LATERAL} runs the inner query once per test id and, thanks to the
     * index on {@code (test_id, executed_at DESC)}, reads only the latest rows instead of each test's whole history
     * (which a {@code ROW_NUMBER()} window over all executions would do).
     */
    @Override
    public Map<UUID, List<TestExecution>> findLatest(Collection<UUID> testIds, int perTest) {
        Map<UUID, List<TestExecution>> latest = new HashMap<>();
        if (testIds.isEmpty()) {
            return latest;
        }
        jdbc.sql("""
                        SELECT ids.test_id, e.run_id, e.commit_sha, e.attempt, e.outcome, e.rerun_failures,
                               e.executed_at
                          FROM unnest(CAST(:testIds AS uuid[])) AS ids (test_id)
                          CROSS JOIN LATERAL (
                                SELECT * FROM test_execution
                                 WHERE test_id = ids.test_id
                                 ORDER BY executed_at DESC
                                 LIMIT :perTest) e
                         ORDER BY ids.test_id, e.executed_at
                        """)
                .param("testIds", testIds.stream().map(UUID::toString).toArray(String[]::new))
                .param("perTest", perTest)
                .query(rs -> {
                    latest.computeIfAbsent(rs.getObject("test_id", UUID.class), id -> new ArrayList<>())
                            .add(new TestExecution(
                                    rs.getObject("run_id", UUID.class),
                                    rs.getString("commit_sha"),
                                    rs.getInt("attempt"),
                                    Outcome.valueOf(rs.getString("outcome")),
                                    rs.getInt("rerun_failures"),
                                    rs.getObject("executed_at", OffsetDateTime.class).toInstant()));
                });
        return latest;
    }
}
