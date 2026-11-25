package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.Optional;

/** No sign of flakiness. Any signal, weak or strong, first makes the test suspicious. */
public final class StableState implements TestState {

    static final StableState INSTANCE = new StableState();

    private StableState() {
    }

    @Override
    public TestStatus status() {
        return TestStatus.STABLE;
    }

    @Override
    public Optional<Transition> onEvaluation(FlakinessSignal signal, int trailingCleanPasses, int recoveryRuns) {
        if (signal.verdict() == Verdict.NONE) {
            return Optional.empty();
        }
        return Optional.of(new Transition(SuspiciousState.INSTANCE, signal.reason()));
    }
}
