package io.github.yagmurpazi.testpulse.analysis.adapter.out.persistence;

import io.github.yagmurpazi.testpulse.analysis.application.port.out.MonitoredTestRepository;
import io.github.yagmurpazi.testpulse.analysis.domain.MonitoredTest;
import io.github.yagmurpazi.testpulse.analysis.domain.TestKey;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Monitored tests in PostgreSQL. Reads for update take row locks that last until the transaction ends. */
@Repository
class JdbcMonitoredTestRepository implements MonitoredTestRepository {

    private static final String COLUMNS =
            "id, repository, class_name, test_name, status, first_seen_at, last_changed_at";

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    JdbcMonitoredTestRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<TestKey, MonitoredTest> findByKeysForUpdate(Collection<TestKey> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        List<String> repositories = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        List<String> testNames = new ArrayList<>();
        for (TestKey key : keys) {
            repositories.add(key.repository());
            classNames.add(key.className());
            testNames.add(key.testName());
        }
        // Three parallel arrays instead of thousands of bind parameters.
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM monitored_test
                        WHERE (repository, class_name, test_name) IN (
                              SELECT * FROM unnest(CAST(:repositories AS text[]), CAST(:classNames AS text[]),
                                                   CAST(:testNames AS text[])))
                          FOR UPDATE
                        """)
                .param("repositories", repositories.toArray(String[]::new))
                .param("classNames", classNames.toArray(String[]::new))
                .param("testNames", testNames.toArray(String[]::new))
                .query(JdbcMonitoredTestRepository::toTest)
                .list().stream()
                .collect(Collectors.toMap(MonitoredTest::key, Function.identity()));
    }

    @Override
    public Optional<MonitoredTest> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM monitored_test WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(JdbcMonitoredTestRepository::toTest)
                .optional();
    }

    @Override
    public void insert(Collection<MonitoredTest> tests) {
        List<MonitoredTest> rows = List.copyOf(tests);
        jdbcTemplate.batchUpdate("""
                        INSERT INTO monitored_test
                            (id, repository, class_name, test_name, status, first_seen_at, last_changed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                rows, 500, (statement, test) -> {
                    statement.setObject(1, test.id());
                    statement.setString(2, test.key().repository());
                    statement.setString(3, test.key().className());
                    statement.setString(4, test.key().testName());
                    statement.setString(5, test.status().name());
                    statement.setObject(6, utc(test.firstSeenAt()));
                    statement.setObject(7, utc(test.lastChangedAt()));
                });
    }

    @Override
    public void updateStatus(MonitoredTest test) {
        jdbc.sql("UPDATE monitored_test SET status = :status, last_changed_at = :lastChangedAt WHERE id = :id")
                .param("status", test.status().name())
                .param("lastChangedAt", utc(test.lastChangedAt()))
                .param("id", test.id())
                .update();
    }

    private static MonitoredTest toTest(ResultSet rs, int row) throws SQLException {
        OffsetDateTime lastChangedAt = rs.getObject("last_changed_at", OffsetDateTime.class);
        return new MonitoredTest(
                rs.getObject("id", UUID.class),
                new TestKey(rs.getString("repository"), rs.getString("class_name"), rs.getString("test_name")),
                TestStatus.valueOf(rs.getString("status")),
                rs.getObject("first_seen_at", OffsetDateTime.class).toInstant(),
                lastChangedAt == null ? null : lastChangedAt.toInstant());
    }

    static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
