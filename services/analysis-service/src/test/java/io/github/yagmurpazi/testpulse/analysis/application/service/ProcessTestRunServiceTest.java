package io.github.yagmurpazi.testpulse.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.ExecutedTest;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunCommand;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessingResult;
import io.github.yagmurpazi.testpulse.analysis.domain.DetectionSettings;
import io.github.yagmurpazi.testpulse.analysis.domain.FlakinessDetector;
import io.github.yagmurpazi.testpulse.analysis.domain.Outcome;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessTestRunServiceTest {

    private static final String REPO = "acme/checkout";
    private static final String CLASS = "io.example.checkout.CheckoutServiceTest";
    private static final String COMMIT = "9f2c4e1a7b3d5f60718293a4b5c6d7e8f9012345";
    private static final Instant T1 = Instant.parse("2026-11-25T10:00:00Z");

    private final InMemoryPorts.Events events = new InMemoryPorts.Events();
    private final InMemoryPorts.Tests tests = new InMemoryPorts.Tests();
    private final InMemoryPorts.Executions executions = new InMemoryPorts.Executions();
    private final InMemoryPorts.Changes changes = new InMemoryPorts.Changes();
    private final ProcessTestRunService service = new ProcessTestRunService(events, tests, executions,
            new StatusChangeRecorder(changes, changes),
            FlakinessDetector.withDefaultStrategies(DetectionSettings.DEFAULTS), DetectionSettings.DEFAULTS,
            UUID::randomUUID);

    @Test
    void firstRunRegistersTestsAndRecordsExecutions() {
        ProcessingResult result = service.process(run(COMMIT, 1, T1,
                test("calculatesTotal", Outcome.PASSED, 0), test("sendsMail", Outcome.SKIPPED, 0)));

        assertThat(result.duplicate()).isFalse();
        assertThat(result.testsEvaluated()).isEqualTo(2);
        assertThat(result.changes()).isEmpty();
        assertThat(tests.inserts()).isEqualTo(2);
        assertThat(executions.count()).isEqualTo(2);
        assertThat(tests.byName("calculatesTotal").status()).isEqualTo(TestStatus.STABLE);
        assertThat(tests.byName("calculatesTotal").firstSeenAt()).isEqualTo(T1);
    }

    @Test
    void storesExecutionDetails() {
        ProcessTestRunCommand command = run(COMMIT, 3, T1, new ExecutedTest(CLASS, "slow", Outcome.PASSED, 1, 1503));

        service.process(command);

        var recorded = executions.of(tests.byName("slow").id()).getFirst();
        assertThat(recorded.durationMillis()).isEqualTo(1503);
        assertThat(recorded.execution().runId()).isEqualTo(command.runId());
        assertThat(recorded.execution().commitSha()).isEqualTo(COMMIT);
        assertThat(recorded.execution().attempt()).isEqualTo(3);
        assertThat(recorded.execution().rerunFailures()).isEqualTo(1);
        assertThat(recorded.execution().executedAt()).isEqualTo(T1);
    }

    @Test
    void ignoresARedeliveredEvent() {
        ProcessTestRunCommand command = run(COMMIT, 1, T1, test("calculatesTotal", Outcome.PASSED, 0));
        service.process(command);

        ProcessingResult redelivered = service.process(command);

        assertThat(redelivered).isEqualTo(new ProcessingResult(true, 0, List.of()));
        assertThat(executions.count()).isEqualTo(1);
        assertThat(tests.inserts()).isEqualTo(1);
    }

    @Test
    void reusesKnownTests() {
        service.process(run(COMMIT, 1, T1, test("calculatesTotal", Outcome.PASSED, 0)));

        service.process(run("aaaaaaa", 1, T1.plusSeconds(60), test("calculatesTotal", Outcome.PASSED, 0)));

        assertThat(tests.inserts()).isEqualTo(1);
        assertThat(executions.count()).isEqualTo(2);
    }

    @Test
    void loadsTheConfiguredWindowOfHistory() {
        service.process(run(COMMIT, 1, T1, test("calculatesTotal", Outcome.PASSED, 0)));

        assertThat(executions.lastPerTest()).isEqualTo(DetectionSettings.DEFAULTS.window());
    }

    @Test
    void countsATestReportedTwiceInOneRunOnce() {
        ProcessingResult result = service.process(run(COMMIT, 1, T1,
                test("duplicated", Outcome.FAILED, 0), test("duplicated", Outcome.PASSED, 0)));

        assertThat(result.testsEvaluated()).isEqualTo(1);
        assertThat(executions.of(tests.byName("duplicated").id()))
                .singleElement().extracting(r -> r.execution().outcome()).isEqualTo(Outcome.PASSED);
    }

    @Test
    void recordsAndPublishesTheChangeCausedByAReRunOfTheSameCommit() {
        service.process(run(COMMIT, 1, T1, test("appliesDiscount", Outcome.ERROR, 0),
                test("calculatesTotal", Outcome.PASSED, 0)));
        ProcessTestRunCommand rerun = run(COMMIT, 2, T1.plusSeconds(300), test("appliesDiscount", Outcome.PASSED, 0),
                test("calculatesTotal", Outcome.PASSED, 0));

        ProcessingResult result = service.process(rerun);

        StatusChange change = result.changes().getFirst();
        assertThat(result.changes()).hasSize(1);
        assertThat(change.key().testName()).isEqualTo("appliesDiscount");
        assertThat(change.from()).isEqualTo(TestStatus.STABLE);
        assertThat(change.to()).isEqualTo(TestStatus.SUSPICIOUS);
        assertThat(change.reason()).isEqualTo("Passed and failed on the same commit 9f2c4e1");
        assertThat(change.changedAt()).isEqualTo(rerun.runTime());
        assertThat(change.triggeringRunId()).isEqualTo(rerun.runId());
        assertThat(changes.logged()).containsExactly(change);
        assertThat(changes.published()).containsExactly(change);
        assertThat(tests.persistedStatus(change.testId())).isEqualTo(TestStatus.SUSPICIOUS);
    }

    @Test
    void demoStoryEndsWithAFlakyTest() {
        service.process(run(COMMIT, 1, T1, test("appliesDiscount", Outcome.ERROR, 0)));
        service.process(run(COMMIT, 2, T1.plusSeconds(300), test("appliesDiscount", Outcome.PASSED, 0)));
        service.process(run("4a1b2c3d4e5f", 1, T1.plusSeconds(3600), test("appliesDiscount", Outcome.PASSED, 1)));

        assertThat(changes.published()).extracting(StatusChange::to)
                .containsExactly(TestStatus.SUSPICIOUS, TestStatus.FLAKY);
        assertThat(tests.persistedStatus(tests.byName("appliesDiscount").id())).isEqualTo(TestStatus.FLAKY);
    }

    private static ProcessTestRunCommand run(String commit, int attempt, Instant time, ExecutedTest... results) {
        return new ProcessTestRunCommand(UUID.randomUUID(), UUID.randomUUID(), REPO, commit, attempt, time,
                List.of(results));
    }

    private static ExecutedTest test(String name, Outcome outcome, int rerunFailures) {
        return new ExecutedTest(CLASS, name, outcome, rerunFailures, 10);
    }
}
