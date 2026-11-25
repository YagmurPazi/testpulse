package io.github.yagmurpazi.testpulse.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestExecutionTest {

    @Test
    void passAfterRerunIsNeitherCleanNorFailure() {
        TestExecution execution = execution("abcdef1234", Outcome.PASSED, 2);

        assertThat(execution.passedOnRerun()).isTrue();
        assertThat(execution.cleanPass()).isFalse();
    }

    @Test
    void passAtFirstAttemptIsClean() {
        TestExecution execution = execution("abcdef1234", Outcome.PASSED, 0);

        assertThat(execution.cleanPass()).isTrue();
        assertThat(execution.passedOnRerun()).isFalse();
    }

    @Test
    void failureIsNeitherCleanNorPassedOnRerun() {
        TestExecution execution = execution("abcdef1234", Outcome.FAILED, 1);

        assertThat(execution.cleanPass()).isFalse();
        assertThat(execution.passedOnRerun()).isFalse();
    }

    @Test
    void shortensCommitToSevenCharacters() {
        assertThat(execution("abcdef1234", Outcome.PASSED, 0).shortCommit()).isEqualTo("abcdef1");
        assertThat(execution("abcdef1", Outcome.PASSED, 0).shortCommit()).isEqualTo("abcdef1");
    }

    @Test
    void rejectsInvalidValues() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new TestExecution(UUID.randomUUID(), "abcdef1", 0, Outcome.PASSED, 0, Instant.EPOCH));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new TestExecution(UUID.randomUUID(), "abcdef1", 1, Outcome.PASSED, -1, Instant.EPOCH));
    }

    @Test
    void outcomeKnowsFailuresAndSkips() {
        assertThat(Outcome.FAILED.isFailure()).isTrue();
        assertThat(Outcome.ERROR.isFailure()).isTrue();
        assertThat(Outcome.PASSED.isFailure()).isFalse();
        assertThat(Outcome.SKIPPED.isFailure()).isFalse();
        assertThat(Outcome.SKIPPED.ran()).isFalse();
        assertThat(Outcome.FAILED.ran()).isTrue();
    }

    private static TestExecution execution(String commit, Outcome outcome, int rerunFailures) {
        return new TestExecution(UUID.randomUUID(), commit, 1, outcome, rerunFailures, Instant.EPOCH);
    }
}
