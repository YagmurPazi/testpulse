package io.github.yagmurpazi.testpulse.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonitoredTestTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final UUID RUN = UUID.randomUUID();
    private static final DetectionSettings SETTINGS = DetectionSettings.DEFAULTS;
    private static final FlakinessDetector DETECTOR = FlakinessDetector.withDefaultStrategies(SETTINGS);

    private final MonitoredTest test = MonitoredTest.firstSeen(UUID.randomUUID(), Histories.KEY, T0);

    @Test
    void startsStableWithoutAnyChange() {
        assertThat(test.status()).isEqualTo(TestStatus.STABLE);
        assertThat(test.statusSince()).isEqualTo(T0);
        assertThat(test.firstSeenAt()).isEqualTo(T0);
        assertThat(test.lastChangedAt()).isNull();
    }

    @Test
    void returnsTheChangeCausedByARunAtTheRunsTime() {
        Histories history = Histories.history().fail("9f2c4e1aaa").pass("9f2c4e1aaa");

        Optional<StatusChange> change = test.evaluate(DETECTOR, history.build(), SETTINGS, RUN, history.lastTime());

        assertThat(change).contains(new StatusChange(test.id(), Histories.KEY, TestStatus.STABLE,
                TestStatus.SUSPICIOUS, "Passed and failed on the same commit 9f2c4e1", history.lastTime(), RUN));
        assertThat(test.statusSince()).isEqualTo(history.lastTime());
        assertThat(test.lastChangedAt()).isEqualTo(history.lastTime());
        assertThat(test.key()).isEqualTo(Histories.KEY);
    }

    @Test
    void evaluatesTheWholeHistoryOfANewTest() {
        // the only evidence is in the very first execution
        Histories history = Histories.history().add("4a1b2c3aaa", Outcome.PASSED, 1);

        assertThat(test.evaluate(DETECTOR, history.build(), SETTINGS, RUN, history.lastTime())).isPresent();
    }

    @Test
    void keepsStatusWhenNothingChanges() {
        Histories history = Histories.history().pass("aaa1111").pass("bbb2222");

        assertThat(test.evaluate(DETECTOR, history.build(), SETTINGS, RUN, history.lastTime())).isEmpty();
        assertThat(test.statusSince()).isEqualTo(T0);
    }

    @Test
    void manualChangesHaveNoTriggeringRun() {
        MonitoredTest flaky = new MonitoredTest(UUID.randomUUID(), Histories.KEY, TestStatus.FLAKY, T0, T0);
        Instant later = T0.plusSeconds(60);

        StatusChange quarantined = flaky.quarantine(later);
        StatusChange released = flaky.release(later.plusSeconds(60));

        assertThat(quarantined.to()).isEqualTo(TestStatus.QUARANTINED);
        assertThat(quarantined.triggeringRunId()).isNull();
        assertThat(released.from()).isEqualTo(TestStatus.QUARANTINED);
        assertThat(released.to()).isEqualTo(TestStatus.SUSPICIOUS);
        assertThat(flaky.statusSince()).isEqualTo(later.plusSeconds(60));
    }

    @Test
    void rejectedActionLeavesTheTestUnchanged() {
        assertThatThrownBy(() -> test.quarantine(T0.plusSeconds(1)))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(test.status()).isEqualTo(TestStatus.STABLE);
        assertThat(test.lastChangedAt()).isNull();
    }

    @Test
    void rejectsHistoryOfAnotherTest() {
        TestHistory other = new TestHistory(new TestKey("acme/checkout", "OtherTest", "x"), List.of());

        assertThatIllegalArgumentException().isThrownBy(() -> test.evaluate(DETECTOR, other, SETTINGS, RUN, T0));
    }

    @Test
    void statusChangeNeedsTwoDifferentStatuses() {
        assertThatIllegalArgumentException().isThrownBy(() -> new StatusChange(UUID.randomUUID(), Histories.KEY,
                TestStatus.FLAKY, TestStatus.FLAKY, "none", T0, null));
    }

    /** The demo story with the real detector: conflict, new evidence, quarantine, release, recovery, no relapse. */
    @Test
    void followsTheLifecycleOfAFlakyTest() {
        Histories history = Histories.history().add("9f2c4e1aaa", Outcome.ERROR, 0);
        assertThat(evaluate(history)).isEmpty();

        history.pass("9f2c4e1aaa");
        assertThat(evaluate(history)).contains(TestStatus.SUSPICIOUS);

        history.pass("5d6e7f8aaa");
        assertThat(evaluate(history)).as("the old conflict does not count twice").isEmpty();

        history.add("4a1b2c3aaa", Outcome.PASSED, 1);
        assertThat(evaluate(history)).as("new evidence after the change").contains(TestStatus.FLAKY);

        test.quarantine(history.lastTime().plusSeconds(1));
        test.release(history.lastTime().plusSeconds(2));
        assertThat(test.status()).isEqualTo(TestStatus.SUSPICIOUS);

        for (int run = 1; run < SETTINGS.recoveryRuns(); run++) {
            history.pass("c" + run + "aaaaaa");
            assertThat(evaluate(history)).as("clean run %d after release", run).isEmpty();
        }
        history.pass("ten0000");
        assertThat(evaluate(history)).contains(TestStatus.STABLE);

        history.pass("more000");
        assertThat(evaluate(history)).as("recovered test does not relapse on old evidence").isEmpty();
    }

    private Optional<TestStatus> evaluate(Histories history) {
        return test.evaluate(DETECTOR, history.build(), SETTINGS, RUN, history.lastTime()).map(StatusChange::to);
    }
}
