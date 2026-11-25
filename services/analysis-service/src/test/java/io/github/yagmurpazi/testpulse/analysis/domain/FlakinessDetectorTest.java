package io.github.yagmurpazi.testpulse.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlakinessDetectorTest {

    @Test
    void keepsTheStrongestSignal() {
        FlakinessDetector detector = new FlakinessDetector(List.of(
                history -> FlakinessSignal.suspicious("weak"),
                history -> FlakinessSignal.flaky("strong"),
                history -> FlakinessSignal.NONE));

        assertThat(detector.evaluate(Histories.of("P"))).isEqualTo(FlakinessSignal.flaky("strong"));
    }

    @Test
    void prefersTheFirstStrategyOnATie() {
        FlakinessDetector detector = new FlakinessDetector(List.of(
                history -> FlakinessSignal.flaky("first"),
                history -> FlakinessSignal.flaky("second")));

        assertThat(detector.evaluate(Histories.of("P")).reason()).isEqualTo("first");
    }

    @Test
    void reportsNothingWhenNoStrategyFindsAnything() {
        FlakinessDetector detector = new FlakinessDetector(List.of(history -> FlakinessSignal.NONE));

        assertThat(detector.evaluate(Histories.of("P"))).isEqualTo(FlakinessSignal.NONE);
    }

    @Test
    void needsAtLeastOneStrategy() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FlakinessDetector(List.of()));
    }

    @Test
    void defaultDetectorRecognisesTheDemoStory() {
        // acme/checkout: timeout on attempt 1, pass on the re-run of the same commit
        TestHistory history = Histories.history().add("9f2c4e1a7b", Outcome.ERROR, 0).pass("9f2c4e1a7b").build();

        FlakinessSignal signal = FlakinessDetector.withDefaultStrategies(DetectionSettings.DEFAULTS).evaluate(history);

        assertThat(signal).isEqualTo(FlakinessSignal.flaky("Passed and failed on the same commit 9f2c4e1"));
    }
}
