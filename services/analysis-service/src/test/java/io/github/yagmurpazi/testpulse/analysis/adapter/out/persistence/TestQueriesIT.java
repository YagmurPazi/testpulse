package io.github.yagmurpazi.testpulse.analysis.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.analysis.IntegrationTest;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ExecutedTest;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunCommand;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunUseCase;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.DailyTrend;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.RepositorySummary;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.TestDetails;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.TestQueries;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.query.TestSummary;
import io.github.yagmurpazi.testpulse.analysis.domain.Outcome;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Dashboard queries against the state produced by the demo story. */
class TestQueriesIT extends IntegrationTest {

    private static final String REPO = "acme/checkout";
    private static final String COMMIT = "9f2c4e1a7b3d5f60718293a4b5c6d7e8f9012345";
    private static final Instant DAY1 = Instant.parse("2026-11-24T10:00:00Z");
    private static final Instant DAY2 = Instant.parse("2026-11-25T10:00:00Z");

    @Autowired
    private ProcessTestRunUseCase processTestRun;

    @Autowired
    private TestQueries queries;

    @BeforeEach
    void playTheDemoStory() {
        processTestRun.process(run(COMMIT, 1, DAY1, Outcome.ERROR, 0));
        processTestRun.process(run(COMMIT, 2, DAY1.plusSeconds(300), Outcome.PASSED, 0));
        processTestRun.process(run("4a1b2c3d4e5f", 1, DAY2, Outcome.PASSED, 1));
    }

    @Test
    void summarisesRepositories() {
        assertThat(queries.repositories())
                .containsExactly(new RepositorySummary(REPO, 2, 1, 0, 1, 0, DAY2));
    }

    @Test
    void listsTheMostSevereTestsFirstWithTheirLatestReason() {
        List<TestSummary> tests = queries.tests(REPO, Optional.empty());

        assertThat(tests).extracting(TestSummary::testName).containsExactly("appliesDiscount", "calculatesTotal");
        TestSummary flaky = tests.getFirst();
        assertThat(flaky.status()).isEqualTo(TestStatus.FLAKY);
        assertThat(flaky.statusSince()).isEqualTo(DAY2);
        assertThat(flaky.lastReason()).startsWith("Passed only after 1 failed attempt");
        assertThat(flaky.executions()).isEqualTo(3);
        assertThat(flaky.failures()).isEqualTo(1);
        assertThat(tests.getLast().lastReason()).isNull();
    }

    @Test
    void filtersByStatus() {
        assertThat(queries.tests(REPO, Optional.of(TestStatus.FLAKY))).hasSize(1);
        assertThat(queries.tests(REPO, Optional.of(TestStatus.QUARANTINED))).isEmpty();
        assertThat(queries.tests("acme/unknown", Optional.empty())).isEmpty();
    }

    @Test
    void showsATestWithItsExecutionsAndStatusHistory() {
        UUID id = queries.tests(REPO, Optional.of(TestStatus.FLAKY)).getFirst().testId();

        TestDetails details = queries.test(id, 2).orElseThrow();

        assertThat(details.className()).isEqualTo("io.example.checkout.CheckoutServiceTest");
        assertThat(details.executions()).extracting(TestDetails.Execution::executedAt)
                .containsExactly(DAY2, DAY1.plusSeconds(300));
        assertThat(details.executions().getFirst().durationMillis()).isEqualTo(1500);
        assertThat(details.statusChanges()).extracting(TestDetails.Change::to)
                .containsExactly(TestStatus.SUSPICIOUS, TestStatus.FLAKY);
        assertThat(queries.test(UUID.randomUUID(), 2)).isEmpty();
    }

    @Test
    void countsExecutionsPerDay() {
        List<DailyTrend> trend = queries.trend(REPO, DAY1.minusSeconds(3600));

        assertThat(trend).containsExactly(
                new DailyTrend(LocalDate.parse("2026-11-24"), 2, 4, 1, 0),
                new DailyTrend(LocalDate.parse("2026-11-25"), 1, 2, 0, 1));
        assertThat(queries.trend(REPO, DAY2.minusSeconds(60))).hasSize(1);
    }

    private static ProcessTestRunCommand run(String commit, int attempt, Instant time, Outcome discount,
            int rerunFailures) {
        return new ProcessTestRunCommand(UUID.randomUUID(), UUID.randomUUID(), REPO, commit, attempt, time, List.of(
                new ExecutedTest("io.example.checkout.CheckoutServiceTest", "appliesDiscount", discount,
                        rerunFailures, 1500),
                new ExecutedTest("io.example.checkout.CheckoutServiceTest", "calculatesTotal", Outcome.PASSED, 0,
                        12)));
    }
}
