package io.github.yagmurpazi.testpulse.analysis.application.service;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.ChangeTestStatusUseCase;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.TestNotFoundException;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.MonitoredTestRepository;
import io.github.yagmurpazi.testpulse.analysis.domain.MonitoredTest;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

/** Quarantine and release, decided by the team. Runs in one transaction, applied by configuration. */
public final class ChangeTestStatusService implements ChangeTestStatusUseCase {

    private final MonitoredTestRepository tests;
    private final StatusChangeRecorder recorder;
    private final Clock clock;

    public ChangeTestStatusService(MonitoredTestRepository tests, StatusChangeRecorder recorder, Clock clock) {
        this.tests = Objects.requireNonNull(tests, "tests");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public StatusChange quarantine(UUID testId) {
        return change(testId, MonitoredTest::quarantine);
    }

    @Override
    public StatusChange release(UUID testId) {
        return change(testId, MonitoredTest::release);
    }

    private StatusChange change(UUID testId, BiFunction<MonitoredTest, Instant, StatusChange> action) {
        MonitoredTest test = tests.findByIdForUpdate(testId).orElseThrow(() -> new TestNotFoundException(testId));
        StatusChange change = action.apply(test, clock.instant());
        tests.updateStatus(test);
        recorder.recordAndPublish(change);
        return change;
    }
}
