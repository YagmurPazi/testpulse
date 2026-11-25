package io.github.yagmurpazi.testpulse.analysis.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yagmurpazi.testpulse.analysis.IntegrationTest;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ChangeTestStatusUseCase;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.MonitoredTestRepository;
import io.github.yagmurpazi.testpulse.analysis.domain.InvalidStatusTransitionException;
import io.github.yagmurpazi.testpulse.analysis.domain.MonitoredTest;
import io.github.yagmurpazi.testpulse.analysis.domain.TestKey;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Manual status changes against PostgreSQL: stored, logged and announced together, or not at all. */
class ChangeTestStatusIT extends IntegrationTest {

    @Autowired
    private ChangeTestStatusUseCase changeTestStatus;

    @Autowired
    private MonitoredTestRepository tests;

    @Test
    void quarantineIsStoredLoggedAndPublished() {
        MonitoredTest flaky = stored(TestStatus.FLAKY);

        changeTestStatus.quarantine(flaky.id());

        assertThat(jdbc.sql("SELECT status FROM monitored_test WHERE id = :id").param("id", flaky.id())
                .query(String.class).single()).isEqualTo("QUARANTINED");
        assertThat(count("status_change WHERE to_status = 'QUARANTINED'")).isEqualTo(1);
        assertThat(count("outbox_event WHERE payload LIKE '%\"newStatus\":\"QUARANTINED\"%'")).isEqualTo(1);
    }

    @Test
    void rejectedActionChangesNothing() {
        MonitoredTest stable = stored(TestStatus.STABLE);

        assertThatThrownBy(() -> changeTestStatus.quarantine(stable.id()))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(count("status_change")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    private MonitoredTest stored(TestStatus status) {
        MonitoredTest test = new MonitoredTest(UUID.randomUUID(),
                new TestKey("acme/checkout", "io.example.CheckoutTest", "appliesDiscount"), status,
                Instant.parse("2026-11-25T10:00:00Z"), null);
        tests.insert(List.of(test));
        return test;
    }
}
