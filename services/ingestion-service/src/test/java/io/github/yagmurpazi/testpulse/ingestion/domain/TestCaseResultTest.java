package io.github.yagmurpazi.testpulse.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TestCaseResultTest {

    @Test
    void passedAfterFailedRerunsCountsAsPassedOnRerun() {
        assertThat(TestData.passedOnRerun("t").passedOnRerun()).isTrue();
    }

    @Test
    void passedWithoutRerunsIsNotPassedOnRerun() {
        assertThat(TestData.passed("t").passedOnRerun()).isFalse();
    }

    @Test
    void failedWithRerunsIsNotPassedOnRerun() {
        TestCaseResult result = new TestCaseResult("C", "t", TestOutcome.FAILED, Duration.ZERO, 2, "boom");

        assertThat(result.passedOnRerun()).isFalse();
    }

    @Test
    void rejectsNegativeDuration() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TestCaseResult("C", "t", TestOutcome.PASSED, Duration.ofMillis(-1), 0, null));
    }

    @Test
    void rejectsNegativeRerunFailures() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TestCaseResult("C", "t", TestOutcome.PASSED, Duration.ZERO, -1, null));
    }

    @Test
    void rejectsBlankNames() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TestCaseResult(" ", "t", TestOutcome.PASSED, Duration.ZERO, 0, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TestCaseResult("C", "", TestOutcome.PASSED, Duration.ZERO, 0, null));
    }

    @Test
    void onlyFailedAndErrorAreFailures() {
        assertThat(TestOutcome.FAILED.isFailure()).isTrue();
        assertThat(TestOutcome.ERROR.isFailure()).isTrue();
        assertThat(TestOutcome.PASSED.isFailure()).isFalse();
        assertThat(TestOutcome.SKIPPED.isFailure()).isFalse();
    }
}
