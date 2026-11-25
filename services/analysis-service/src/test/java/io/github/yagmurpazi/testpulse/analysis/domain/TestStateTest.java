package io.github.yagmurpazi.testpulse.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Every cell of the transition table. */
class TestStateTest {

    private static final int RECOVERY_RUNS = 10;

    @ParameterizedTest(name = "{0} + {1} with {2} clean passes -> {3}")
    @CsvSource({
        "STABLE,      NONE,       9,  STABLE",
        "STABLE,      NONE,       10, STABLE",
        "STABLE,      SUSPICIOUS, 0,  SUSPICIOUS",
        "STABLE,      FLAKY,      0,  SUSPICIOUS",
        "SUSPICIOUS,  NONE,       9,  SUSPICIOUS",
        "SUSPICIOUS,  NONE,       10, STABLE",
        "SUSPICIOUS,  SUSPICIOUS, 10, SUSPICIOUS",
        "SUSPICIOUS,  FLAKY,      10, FLAKY",
        "FLAKY,       NONE,       9,  FLAKY",
        "FLAKY,       NONE,       10, STABLE",
        "FLAKY,       SUSPICIOUS, 10, FLAKY",
        "FLAKY,       FLAKY,      10, FLAKY",
        "QUARANTINED, NONE,       10, QUARANTINED",
        "QUARANTINED, SUSPICIOUS, 0,  QUARANTINED",
        "QUARANTINED, FLAKY,      0,  QUARANTINED"
    })
    void reactsToEvaluations(TestStatus from, Verdict verdict, int cleanPasses, TestStatus expected) {
        FlakinessSignal signal = verdict == Verdict.NONE ? FlakinessSignal.NONE
                : new FlakinessSignal(verdict, "evidence");

        Optional<Transition> transition = TestState.of(from).onEvaluation(signal, cleanPasses, RECOVERY_RUNS);

        TestStatus actual = transition.map(t -> t.target().status()).orElse(from);
        assertThat(actual).isEqualTo(expected);
        assertThat(transition.isPresent()).as("a transition only when the status changes").isEqualTo(from != expected);
    }

    @Test
    void signalReasonExplainsTheTransition() {
        Transition transition = TestState.of(TestStatus.STABLE)
                .onEvaluation(FlakinessSignal.flaky("Passed and failed on the same commit 9f2c4e1"), 0, RECOVERY_RUNS)
                .orElseThrow();

        assertThat(transition.reason()).isEqualTo("Passed and failed on the same commit 9f2c4e1");
    }

    @Test
    void recoveryExplainsTheStreak() {
        Transition transition = TestState.of(TestStatus.FLAKY)
                .onEvaluation(FlakinessSignal.NONE, 12, RECOVERY_RUNS).orElseThrow();

        assertThat(transition.reason()).isEqualTo("Passed cleanly 12 times in a row");
    }

    @Test
    void onlyFlakyTestsCanBeQuarantined() {
        assertThat(TestState.of(TestStatus.FLAKY).quarantine().target().status()).isEqualTo(TestStatus.QUARANTINED);

        for (TestStatus status : new TestStatus[] {TestStatus.STABLE, TestStatus.SUSPICIOUS, TestStatus.QUARANTINED}) {
            assertThatThrownBy(() -> TestState.of(status).quarantine())
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessage("Cannot quarantine a test that is " + status);
        }
    }

    @Test
    void onlyQuarantinedTestsCanBeReleasedAndAreWatchedAfterwards() {
        assertThat(TestState.of(TestStatus.QUARANTINED).release().target().status())
                .isEqualTo(TestStatus.SUSPICIOUS);

        for (TestStatus status : new TestStatus[] {TestStatus.STABLE, TestStatus.SUSPICIOUS, TestStatus.FLAKY}) {
            assertThatThrownBy(() -> TestState.of(status).release())
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(TestStatus.class)
    void mapsEveryStatusToItsState(TestStatus status) {
        assertThat(TestState.of(status).status()).isEqualTo(status);
    }

    @Test
    void invalidTransitionNamesStatusAndAction() {
        InvalidStatusTransitionException exception =
                new InvalidStatusTransitionException(TestStatus.STABLE, "release");

        assertThat(exception.currentStatus()).isEqualTo(TestStatus.STABLE);
        assertThat(exception.action()).isEqualTo("release");
    }

    @Test
    void transitionNeedsAReason() {
        assertThatThrownBy(() -> new Transition(TestState.of(TestStatus.STABLE), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
