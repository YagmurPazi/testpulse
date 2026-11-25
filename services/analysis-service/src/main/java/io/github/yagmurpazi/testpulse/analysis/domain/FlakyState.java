package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.Optional;

/** Flaky: the team can quarantine it; if it recovers on its own, it becomes stable again. */
public final class FlakyState implements TestState {

    static final FlakyState INSTANCE = new FlakyState();

    private FlakyState() {
    }

    @Override
    public TestStatus status() {
        return TestStatus.FLAKY;
    }

    @Override
    public Optional<Transition> onEvaluation(FlakinessSignal signal, int trailingCleanPasses, int recoveryRuns) {
        return TestState.recovery(signal, trailingCleanPasses, recoveryRuns);
    }

    @Override
    public Transition quarantine() {
        return new Transition(QuarantinedState.INSTANCE, "Quarantined by the team");
    }
}
