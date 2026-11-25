package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.Optional;

/**
 * Behaviour of one test status (State pattern). Each state decides on its own which transitions it allows, so the
 * rules for a status live in one class instead of in switch statements spread over the code base.
 *
 * <pre>
 *   Stable ──signal──▶ Suspicious ──strong signal──▶ Flaky ──quarantine──▶ Quarantined
 *     ▲                    │                           │                       │
 *     └────recovered───────┴──────────recovered────────┘                       │
 *                          ▲                                                   │
 *                          └───────────────────────release─────────────────────┘
 * </pre>
 *
 * <p>"Recovered" means: no signal in the executions since the last change, and at least {@code recoveryRuns} clean
 * passes in a row.
 */
public sealed interface TestState permits StableState, SuspiciousState, FlakyState, QuarantinedState {

    TestStatus status();

    /** Reacts to the evaluation after a new run; empty if the status stays. */
    Optional<Transition> onEvaluation(FlakinessSignal signal, int trailingCleanPasses, int recoveryRuns);

    /** Manual decision to stop trusting the test. */
    default Transition quarantine() {
        throw new InvalidStatusTransitionException(status(), "quarantine");
    }

    /** Manual decision to trust the test again. */
    default Transition release() {
        throw new InvalidStatusTransitionException(status(), "release");
    }

    static TestState of(TestStatus status) {
        return switch (status) {
            case STABLE -> StableState.INSTANCE;
            case SUSPICIOUS -> SuspiciousState.INSTANCE;
            case FLAKY -> FlakyState.INSTANCE;
            case QUARANTINED -> QuarantinedState.INSTANCE;
        };
    }

    /** Shared rule of Suspicious and Flaky: back to Stable once the evidence is gone and the test passes reliably. */
    static Optional<Transition> recovery(FlakinessSignal signal, int trailingCleanPasses, int recoveryRuns) {
        if (signal.verdict() == Verdict.NONE && trailingCleanPasses >= recoveryRuns) {
            return Optional.of(new Transition(StableState.INSTANCE,
                    "Passed cleanly " + trailingCleanPasses + " times in a row"));
        }
        return Optional.empty();
    }
}
