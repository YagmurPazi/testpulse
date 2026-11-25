package io.github.yagmurpazi.testpulse.analysis.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.analysis.IntegrationTest;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.MonitoredTestRepository;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.ProcessedEvents;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.RecordedExecution;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.StatusChangeLog;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.TestExecutionRepository;
import io.github.yagmurpazi.testpulse.analysis.domain.MonitoredTest;
import io.github.yagmurpazi.testpulse.analysis.domain.Outcome;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import io.github.yagmurpazi.testpulse.analysis.domain.TestExecution;
import io.github.yagmurpazi.testpulse.analysis.domain.TestKey;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class AnalysisPersistenceIT extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-25T10:00:00Z");
    private static final TestKey DISCOUNT = new TestKey("acme/checkout", "io.example.CheckoutTest", "appliesDiscount");
    private static final TestKey TOTAL = new TestKey("acme/checkout", "io.example.CheckoutTest", "calculatesTotal");
    private static final TestKey OTHER_REPO = new TestKey("acme/shop", "io.example.CheckoutTest", "appliesDiscount");

    @Autowired
    private MonitoredTestRepository tests;

    @Autowired
    private TestExecutionRepository executions;

    @Autowired
    private ProcessedEvents processedEvents;

    @Autowired
    private StatusChangeLog statusChanges;

    @Autowired
    private TransactionTemplate transaction;

    @Test
    void findsTestsByKeyAcrossRepositoriesAndIgnoresUnknownKeys() {
        MonitoredTest discount = MonitoredTest.firstSeen(UUID.randomUUID(), DISCOUNT, T0);
        MonitoredTest otherRepo = MonitoredTest.firstSeen(UUID.randomUUID(), OTHER_REPO, T0);
        tests.insert(List.of(discount, otherRepo));

        Map<TestKey, MonitoredTest> found = transaction.execute(
                status -> tests.findByKeysForUpdate(List.of(DISCOUNT, OTHER_REPO, TOTAL)));

        assertThat(found).containsOnlyKeys(DISCOUNT, OTHER_REPO);
        assertThat(found.get(DISCOUNT).id()).isEqualTo(discount.id());
        assertThat(found.get(DISCOUNT).status()).isEqualTo(TestStatus.STABLE);
        assertThat(found.get(DISCOUNT).firstSeenAt()).isEqualTo(T0);
        assertThat(found.get(DISCOUNT).lastChangedAt()).isNull();
        assertThat(tests.findByKeysForUpdate(List.of())).isEmpty();
    }

    @Test
    void storesStatusChanges() {
        MonitoredTest flaky = new MonitoredTest(UUID.randomUUID(), DISCOUNT, TestStatus.FLAKY, T0, T0);
        tests.insert(List.of(flaky));
        StatusChange change = flaky.quarantine(T0.plusSeconds(60));

        tests.updateStatus(flaky);
        statusChanges.record(change);

        MonitoredTest reloaded = tests.findByIdForUpdate(flaky.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(TestStatus.QUARANTINED);
        assertThat(reloaded.lastChangedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(count("status_change WHERE to_status = 'QUARANTINED' AND triggering_run_id IS NULL"))
                .isEqualTo(1);
        assertThat(tests.findByIdForUpdate(UUID.randomUUID())).isEmpty();
    }

    @Test
    void returnsTheLatestExecutionsPerTestOldestFirst() {
        MonitoredTest discount = MonitoredTest.firstSeen(UUID.randomUUID(), DISCOUNT, T0);
        MonitoredTest total = MonitoredTest.firstSeen(UUID.randomUUID(), TOTAL, T0);
        MonitoredTest neverRan = MonitoredTest.firstSeen(UUID.randomUUID(), OTHER_REPO, T0);
        tests.insert(List.of(discount, total, neverRan));
        List<RecordedExecution> recorded = new ArrayList<>();
        for (int run = 0; run < 5; run++) {
            recorded.add(execution(discount, run, run % 2 == 0 ? Outcome.PASSED : Outcome.FAILED));
        }
        recorded.add(execution(total, 0, Outcome.SKIPPED));
        executions.appendAll(recorded);

        Map<UUID, List<TestExecution>> latest =
                executions.findLatest(List.of(discount.id(), total.id(), neverRan.id()), 3);

        assertThat(latest).containsOnlyKeys(discount.id(), total.id());
        assertThat(latest.get(discount.id())).extracting(TestExecution::executedAt)
                .containsExactly(T0.plusSeconds(2), T0.plusSeconds(3), T0.plusSeconds(4));
        assertThat(latest.get(discount.id()).getFirst().outcome()).isEqualTo(Outcome.PASSED);
        assertThat(latest.get(total.id())).singleElement().extracting(TestExecution::outcome)
                .isEqualTo(Outcome.SKIPPED);
        assertThat(executions.findLatest(List.of(), 3)).isEmpty();
    }

    @Test
    void marksAnEventAsProcessedOnlyOnce() {
        UUID eventId = UUID.randomUUID();

        assertThat(processedEvents.markProcessed(eventId)).isTrue();
        assertThat(processedEvents.markProcessed(eventId)).isFalse();
    }

    @Test
    void blocksASecondWriterUntilTheFirstTransactionEnds() throws Exception {
        MonitoredTest flaky = new MonitoredTest(UUID.randomUUID(), DISCOUNT, TestStatus.FLAKY, T0, T0);
        tests.insert(List.of(flaky));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            tests.findByIdForUpdate(flaky.id());
            locked.countDown();
            await(release);
        }));
        locked.await(10, TimeUnit.SECONDS);
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(
                status -> tests.findByIdForUpdate(flaky.id())));

        Thread.sleep(300);
        assertThat(second).as("second reader waits for the row lock").isNotDone();
        release.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
    }

    private static RecordedExecution execution(MonitoredTest test, int run, Outcome outcome) {
        return new RecordedExecution(test.id(), new TestExecution(UUID.randomUUID(), "abc1234", 1, outcome, 0,
                T0.plusSeconds(run)), 10);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
