package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.Optional;

/** Under observation: strong evidence makes the test flaky, a reliable streak makes it stable again. */
public final class SuspiciousState implements TestState {

    static final SuspiciousState INSTANCE = new SuspiciousState();

    private SuspiciousState() {
    }

    @Override
    public TestStatus status() {
        return TestStatus.SUSPICIOUS;
    }

    @Override
    public Optional<Transition> onEvaluation(FlakinessSignal signal, int trailingCleanPasses, int recoveryRuns) {
        if (signal.verdict() == Verdict.FLAKY) {
            return Optional.of(new Transition(FlakyState.INSTANCE, signal.reason()));
        }
        return TestState.recovery(signal, trailingCleanPasses, recoveryRuns);
    }
}
