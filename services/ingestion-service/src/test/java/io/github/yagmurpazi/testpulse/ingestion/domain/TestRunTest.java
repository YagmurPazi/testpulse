package io.github.yagmurpazi.testpulse.ingestion.domain;

import static io.github.yagmurpazi.testpulse.ingestion.domain.TestData.IDENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestRunTest {

    @Test
    void summarisesOutcomes() {
        TestRun run = TestData.run(IDENTITY, List.of(
                TestData.passed("a"),
                TestData.passedOnRerun("b"),
                TestData.failed("c"),
                new TestCaseResult("C", "d", TestOutcome.ERROR, Duration.ZERO, 0, "NPE"),
                new TestCaseResult("C", "e", TestOutcome.SKIPPED, Duration.ZERO, 0, null)));

        assertThat(run.summary()).isEqualTo(new RunSummary(5, 2, 1, 1, 1, 1));
    }

    @Test
    void rejectsRunWithoutResults() {
        assertThatIllegalArgumentException().isThrownBy(() -> TestData.run(IDENTITY, List.of()))
                .withMessageContaining("at least one");
    }

    @Test
    void rejectsBlankBranch() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TestRun(UUID.randomUUID(), IDENTITY,
                TestData.COMMIT, " ", TestData.NOW, List.of(TestData.passed("a"))));
    }

    @Test
    void isNotAffectedByLaterChangesToTheInputList() {
        List<TestCaseResult> results = new ArrayList<>(List.of(TestData.passed("a")));
        TestRun run = TestData.run(IDENTITY, results);

        results.add(TestData.failed("b"));

        assertThat(run.results()).hasSize(1);
        assertThatThrownBy(() -> run.results().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
