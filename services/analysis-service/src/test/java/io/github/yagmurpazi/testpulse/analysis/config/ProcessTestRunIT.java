package io.github.yagmurpazi.testpulse.analysis.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yagmurpazi.testpulse.analysis.IntegrationTest;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ExecutedTest;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunCommand;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunUseCase;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessingResult;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.MonitoredTestRepository;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.ProcessedEvents;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.StatusChangeLog;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.TestExecutionRepository;
import io.github.yagmurpazi.testpulse.analysis.application.service.ProcessTestRunService;
import io.github.yagmurpazi.testpulse.analysis.application.service.StatusChangeRecorder;
import io.github.yagmurpazi.testpulse.analysis.domain.DetectionSettings;
import io.github.yagmurpazi.testpulse.analysis.domain.FlakinessDetector;
import io.github.yagmurpazi.testpulse.analysis.domain.Outcome;
import io.github.yagmurpazi.testpulse.events.TestStatus;
import io.github.yagmurpazi.testpulse.events.TestStatusChanged;
import io.github.yagmurpazi.testpulse.events.Topics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** The processing use case against PostgreSQL: the demo story, redelivery, and rollback on failure. */
class ProcessTestRunIT extends IntegrationTest {

    private static final String COMMIT = "9f2c4e1a7b3d5f60718293a4b5c6d7e8f9012345";
    private static final Instant T1 = Instant.parse("2026-11-25T10:00:00Z");

    @Autowired
    private ProcessTestRunUseCase processTestRun;

    @Autowired
    private ProcessedEvents processedEvents;

    @Autowired
    private MonitoredTestRepository tests;

    @Autowired
    private TestExecutionRepository executions;

    @Autowired
    private StatusChangeLog statusChangeLog;

    @Autowired
    private DetectionSettings settings;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void demoStoryMakesTheTestFlakyAndPublishesBothChanges() {
        processTestRun.process(run(COMMIT, 1, T1, Outcome.ERROR, 0));
        processTestRun.process(run(COMMIT, 2, T1.plusSeconds(300), Outcome.PASSED, 0));
        processTestRun.process(run("4a1b2c3d4e5f", 1, T1.plusSeconds(3600), Outcome.PASSED, 1));

        assertThat(jdbc.sql("SELECT status FROM monitored_test WHERE test_name = 'appliesDiscount'")
                .query(String.class).single()).isEqualTo("FLAKY");
        assertThat(count("monitored_test")).isEqualTo(2);
        assertThat(count("test_execution")).isEqualTo(6);
        assertThat(count("status_change")).isEqualTo(2);
        List<TestStatusChanged> events = jdbc.sql(
                        "SELECT payload FROM outbox_event WHERE topic = :topic ORDER BY created_at")
                .param("topic", Topics.TEST_STATUS_CHANGES)
                .query(String.class).list().stream()
                .map(payload -> jsonMapper.readValue(payload, TestStatusChanged.class)).toList();
        assertThat(events).extracting(TestStatusChanged::newStatus)
                .containsExactly(TestStatus.SUSPICIOUS, TestStatus.FLAKY);
        assertThat(events.getLast().reason())
                .isEqualTo("Passed only after 1 failed attempt(s) within one run on commit 4a1b2c3");
    }

    @Test
    void ignoresARedeliveredEvent() {
        ProcessTestRunCommand command = run(COMMIT, 1, T1, Outcome.PASSED, 0);
        processTestRun.process(command);

        ProcessingResult redelivered = processTestRun.process(command);

        assertThat(redelivered.duplicate()).isTrue();
        assertThat(count("test_execution")).isEqualTo(2);
        assertThat(count("processed_event")).isEqualTo(1);
    }

    @Test
    void rollsBackEverythingIncludingTheProcessedMarkerWhenProcessingFails() {
        ProcessTestRunUseCase failingPublisher = TransactionalUseCases.processTestRun(
                new ProcessTestRunService(processedEvents, tests, executions,
                        new StatusChangeRecorder(statusChangeLog, change -> {
                            throw new IllegalStateException("outbox unavailable");
                        }),
                        FlakinessDetector.withDefaultStrategies(settings), settings, UUID::randomUUID),
                transaction);
        processTestRun.process(run(COMMIT, 1, T1, Outcome.ERROR, 0));
        ProcessTestRunCommand causesChange = run(COMMIT, 2, T1.plusSeconds(300), Outcome.PASSED, 0);

        assertThatThrownBy(() -> failingPublisher.process(causesChange)).hasMessage("outbox unavailable");

        assertThat(count("processed_event")).as("event will be processed again on redelivery").isEqualTo(1);
        assertThat(count("test_execution")).isEqualTo(2);
        assertThat(count("status_change")).isZero();
        assertThat(processTestRun.process(causesChange).changes()).hasSize(1);
    }

    private static ProcessTestRunCommand run(String commit, int attempt, Instant time, Outcome discount,
            int rerunFailures) {
        return new ProcessTestRunCommand(UUID.randomUUID(), UUID.randomUUID(), "acme/checkout", commit, attempt,
                time, List.of(
                        new ExecutedTest("io.example.checkout.CheckoutServiceTest", "appliesDiscount", discount,
                                rerunFailures, 1500),
                        new ExecutedTest("io.example.checkout.CheckoutServiceTest", "calculatesTotal",
                                Outcome.PASSED, 0, 12)));
    }
}
