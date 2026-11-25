package io.github.yagmurpazi.testpulse.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestHistoryTest {

    @Test
    void ordersExecutionsOldestFirst() {
        TestExecution later =
                new TestExecution(UUID.randomUUID(), "b", 1, Outcome.FAILED, 0, Instant.ofEpochSecond(2));
        TestExecution earlier =
                new TestExecution(UUID.randomUUID(), "a", 1, Outcome.PASSED, 0, Instant.ofEpochSecond(1));

        TestHistory history = new TestHistory(Histories.KEY, List.of(later, earlier));

        assertThat(history.executions()).containsExactly(earlier, later);
    }

    @Test
    void ignoresSkippedExecutionsWhenLookingAtRuns() {
        assertThat(Histories.of("PSFS").ran()).extracting(TestExecution::outcome)
                .containsExactly(Outcome.PASSED, Outcome.FAILED);
    }

    @Test
    void countsCleanPassesAtTheEndSkippingSkips() {
        assertThat(Histories.of("FPPSP").trailingCleanPasses()).isEqualTo(3);
    }

    @Test
    void countsEveryRunWhenAllPassedCleanly() {
        assertThat(Histories.of("PPP").trailingCleanPasses()).isEqualTo(3);
    }

    @Test
    void passAfterRerunEndsTheCleanStreak() {
        assertThat(Histories.of("PPRP").trailingCleanPasses()).isEqualTo(1);
    }

    @Test
    void hasNoCleanPassesWhenLastRunFailed() {
        assertThat(Histories.of("PPF").trailingCleanPasses()).isZero();
        assertThat(Histories.of("").trailingCleanPasses()).isZero();
    }
}
