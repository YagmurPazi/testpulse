package io.github.yagmurpazi.testpulse.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.TestNotFoundException;
import io.github.yagmurpazi.testpulse.analysis.domain.InvalidStatusTransitionException;
import io.github.yagmurpazi.testpulse.analysis.domain.MonitoredTest;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import io.github.yagmurpazi.testpulse.analysis.domain.TestKey;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangeTestStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-26T09:00:00Z");
    private static final TestKey KEY = new TestKey("acme/checkout", "CheckoutServiceTest", "appliesDiscount");

    private final InMemoryPorts.Tests tests = new InMemoryPorts.Tests();
    private final InMemoryPorts.Changes changes = new InMemoryPorts.Changes();
    private final ChangeTestStatusService service = new ChangeTestStatusService(tests,
            new StatusChangeRecorder(changes, changes), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void quarantinesAFlakyTest() {
        MonitoredTest flaky = stored(TestStatus.FLAKY);

        StatusChange change = service.quarantine(flaky.id());

        assertThat(change.to()).isEqualTo(TestStatus.QUARANTINED);
        assertThat(change.changedAt()).isEqualTo(NOW);
        assertThat(change.triggeringRunId()).isNull();
        assertThat(tests.persistedStatus(flaky.id())).isEqualTo(TestStatus.QUARANTINED);
        assertThat(changes.logged()).containsExactly(change);
        assertThat(changes.published()).containsExactly(change);
    }

    @Test
    void releasesAQuarantinedTestForObservation() {
        MonitoredTest quarantined = stored(TestStatus.QUARANTINED);

        StatusChange change = service.release(quarantined.id());

        assertThat(change.to()).isEqualTo(TestStatus.SUSPICIOUS);
        assertThat(tests.persistedStatus(quarantined.id())).isEqualTo(TestStatus.SUSPICIOUS);
    }

    @Test
    void rejectsUnknownTest() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.quarantine(unknown))
                .isInstanceOf(TestNotFoundException.class).hasMessageContaining(unknown.toString());
    }

    @Test
    void rejectsInvalidActionWithoutRecordingAnything() {
        MonitoredTest stable = stored(TestStatus.STABLE);

        assertThatThrownBy(() -> service.quarantine(stable.id()))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> service.release(stable.id()))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(tests.persistedStatus(stable.id())).isEqualTo(TestStatus.STABLE);
        assertThat(changes.logged()).isEmpty();
        assertThat(changes.published()).isEmpty();
    }

    private MonitoredTest stored(TestStatus status) {
        MonitoredTest test = new MonitoredTest(UUID.randomUUID(), KEY, status, NOW.minusSeconds(3600), null);
        tests.add(test);
        return test;
    }
}
