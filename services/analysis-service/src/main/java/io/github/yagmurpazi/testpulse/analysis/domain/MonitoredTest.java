package io.github.yagmurpazi.testpulse.analysis.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A test that TestPulse watches: its identity and current status. The aggregate root of the analysis domain; all
 * status changes go through it, and every change is returned so it can be recorded and published.
 *
 * <p><b>Evidence starts fresh after every status change.</b> Only executions after the last change are evaluated.
 * Otherwise old evidence still inside the window would push a released test straight back to flaky, or make a
 * recovered test oscillate between stable and suspicious. All times are event times (when a run was ingested), so
 * replaying the event stream gives the same result.
 */
public final class MonitoredTest {

    private final UUID id;
    private final TestKey key;
    private final Instant firstSeenAt;
    private TestState state;
    private Instant lastChangedAt;

    /**
     * @param lastChangedAt time of the last status change, {@code null} if the status never changed
     */
    public MonitoredTest(UUID id, TestKey key, TestStatus status, Instant firstSeenAt, Instant lastChangedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.key = Objects.requireNonNull(key, "key");
        this.state = TestState.of(Objects.requireNonNull(status, "status"));
        this.firstSeenAt = Objects.requireNonNull(firstSeenAt, "firstSeenAt");
        this.lastChangedAt = lastChangedAt;
    }

    /** A test seen for the first time starts as stable; its whole history counts as evidence. */
    public static MonitoredTest firstSeen(UUID id, TestKey key, Instant seenAt) {
        return new MonitoredTest(id, key, TestStatus.STABLE, seenAt, null);
    }

    /**
     * Evaluates the history after a run and applies the resulting transition.
     *
     * @param runTime event time of the run, becomes the time of the change
     * @return the change if the status changed
     */
    public Optional<StatusChange> evaluate(FlakinessDetector detector, TestHistory history,
            DetectionSettings settings, UUID runId, Instant runTime) {
        if (!history.key().equals(key)) {
            throw new IllegalArgumentException("history of " + history.key() + " evaluated for " + key);
        }
        TestHistory evidence = lastChangedAt == null ? history : history.after(lastChangedAt);
        FlakinessSignal signal = detector.evaluate(evidence);
        return state.onEvaluation(signal, evidence.trailingCleanPasses(), settings.recoveryRuns())
                .map(transition -> apply(transition, runTime, runId));
    }

    /** @throws InvalidStatusTransitionException unless the test is flaky */
    public StatusChange quarantine(Instant now) {
        return apply(state.quarantine(), now, null);
    }

    /** @throws InvalidStatusTransitionException unless the test is quarantined */
    public StatusChange release(Instant now) {
        return apply(state.release(), now, null);
    }

    private StatusChange apply(Transition transition, Instant at, UUID runId) {
        TestStatus from = state.status();
        state = transition.target();
        lastChangedAt = at;
        return new StatusChange(id, key, from, state.status(), transition.reason(), at, runId);
    }

    public UUID id() {
        return id;
    }

    public TestKey key() {
        return key;
    }

    public TestStatus status() {
        return state.status();
    }

    public Instant firstSeenAt() {
        return firstSeenAt;
    }

    /** {@code null} if the status never changed. */
    public Instant lastChangedAt() {
        return lastChangedAt;
    }

    /** Since when the test has its current status. */
    public Instant statusSince() {
        return lastChangedAt != null ? lastChangedAt : firstSeenAt;
    }
}
