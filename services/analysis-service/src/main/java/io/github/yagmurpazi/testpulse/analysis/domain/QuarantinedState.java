package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.Optional;

/**
 * The team stopped trusting the test. Automatic evaluations do not change this decision; only a release does, and
 * the released test is watched as suspicious before it counts as stable.
 */
public final class QuarantinedState implements TestState {

    static final QuarantinedState INSTANCE = new QuarantinedState();

    private QuarantinedState() {
    }

    @Override
    public TestStatus status() {
        return TestStatus.QUARANTINED;
    }

    @Override
    public Optional<Transition> onEvaluation(FlakinessSignal signal, int trailingCleanPasses, int recoveryRuns) {
        return Optional.empty();
    }

    @Override
    public Transition release() {
        return new Transition(SuspiciousState.INSTANCE, "Released from quarantine by the team");
    }
}
