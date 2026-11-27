package io.github.yagmurpazi.testpulse.ingestion.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.ingestion.IntegrationTest;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunRepository;
import io.github.yagmurpazi.testpulse.ingestion.domain.RunIdentity;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestData;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestOutcome;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcTestRunRepositoryIT extends IntegrationTest {

    @Autowired
    private TestRunRepository repository;

    @Autowired
    private TransactionTemplate transaction;

    @Test
    void readsBackExactlyWhatWasStored() {
        TestRun run = TestData.run(TestData.IDENTITY, List.of(
                TestData.passedOnRerun("first"),
                TestData.failed("second"),
                new TestCaseResult("io.example.SampleTest", "third [1] value=ä", TestOutcome.SKIPPED,
                        Duration.ofMillis(1234), 0, null)));

        assertThat(repository.saveIfAbsent(run)).isTrue();

        assertThat(repository.findByIdentity(TestData.IDENTITY)).contains(run);
    }

    @Test
    void findsNothingForUnknownIdentity() {
        assertThat(repository.findByIdentity(TestData.IDENTITY)).isEmpty();
    }

    @Test
    void keepsFirstRunWhenSameIdentityIsSavedAgain() {
        TestRun first = TestData.run(TestData.IDENTITY, List.of(TestData.passed("a")));
        TestRun second = TestData.run(TestData.IDENTITY, List.of(TestData.failed("b")));

        assertThat(repository.saveIfAbsent(first)).isTrue();
        assertThat(repository.saveIfAbsent(second)).isFalse();

        assertThat(repository.findByIdentity(TestData.IDENTITY)).contains(first);
        assertThat(count("test_case_result")).isEqualTo(1);
    }

    @Test
    void storesAnotherAttemptAsSeparateRun() {
        RunIdentity rerun = new RunIdentity(TestData.IDENTITY.repository(), TestData.IDENTITY.externalRunId(), 2);

        repository.saveIfAbsent(TestData.run(TestData.IDENTITY, List.of(TestData.passed("a"))));
        repository.saveIfAbsent(TestData.run(rerun, List.of(TestData.failed("a"))));

        assertThat(count("test_run")).isEqualTo(2);
    }

    @Test
    void storesLargeRunsInBatches() {
        List<TestCaseResult> results = IntStream.range(0, 1_200).mapToObj(i -> TestData.passed("test" + i)).toList();

        repository.saveIfAbsent(TestData.run(TestData.IDENTITY, results));

        assertThat(repository.findByIdentity(TestData.IDENTITY).orElseThrow().results()).isEqualTo(results);
    }

    @Test
    void letsExactlyOneOfManyConcurrentTransactionsStoreTheSameIdentity() throws Exception {
        int writers = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            TestRun candidate = TestData.run(TestData.IDENTITY, List.of(TestData.passed("writer" + i)));
            tasks.add(() -> {
                start.await();
                return transaction.execute(status -> repository.saveIfAbsent(candidate));
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            List<Future<Boolean>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            List<Boolean> outcomes = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(outcomes).containsOnlyOnce(true);
            assertThat(count("test_run")).isEqualTo(1);
            assertThat(count("test_case_result")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
