package io.github.yagmurpazi.testpulse.analysis.adapter.out.persistence;

import io.github.yagmurpazi.testpulse.analysis.application.port.out.StatusChangeLog;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** History of status changes. */
@Repository
class JdbcStatusChangeLog implements StatusChangeLog {

    private final JdbcClient jdbc;

    JdbcStatusChangeLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(StatusChange change) {
        jdbc.sql("""
                        INSERT INTO status_change
                            (id, test_id, from_status, to_status, reason, changed_at, triggering_run_id)
                        VALUES (:id, :testId, :from, :to, :reason, :changedAt, :runId)
                        """)
                .param("id", UUID.randomUUID())
                .param("testId", change.testId())
                .param("from", change.from().name())
                .param("to", change.to().name())
                .param("reason", change.reason())
                .param("changedAt", JdbcMonitoredTestRepository.utc(change.changedAt()))
                .param("runId", change.triggeringRunId())
                .update();
    }
}
