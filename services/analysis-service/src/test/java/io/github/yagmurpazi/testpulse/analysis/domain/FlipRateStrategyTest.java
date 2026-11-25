package io.github.yagmurpazi.testpulse.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FlipRateStrategyTest {

    /** 21 runs give 20 pairs, so 3 flips are exactly 0.15 and 6 flips exactly 0.30. */
    private static final DetectionSettings SETTINGS = new DetectionSettings(21, 5, 3, 0.15, 0.3, 10);

    private final FlipRateStrategy strategy = new FlipRateStrategy(SETTINGS);

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        // fixed regression: two switches are not flakiness
        "PPPPPPPPFFFFFPPPPPPPP, NONE",
        // broken test: never switches
        "FFFFFFFFFFFFFFFFFFFFF, NONE",
        // exactly at the suspicious threshold: 3 flips / 20 pairs = 0.15
        "PPPPPPPPPPPPPPPPPFPFF, SUSPICIOUS",
        // just below the flaky threshold: 5 flips / 20 pairs = 0.25
        "PPPPPPPPPPPPPPPFPFPFF, SUSPICIOUS",
        // exactly at the flaky threshold: 6 flips / 20 pairs = 0.30
        "PPPPPPPPPPPPPPFPFPFPP, FLAKY",
        "PFPFPFPFPFPFPFPFPFPFP, FLAKY"
    })
    void classifiesByFlipRate(String outcomes, Verdict expected) {
        assertThat(strategy.evaluate(Histories.of(outcomes)).verdict()).isEqualTo(expected);
    }

    @Test
    void needsMinimumSamples() {
        assertThat(strategy.evaluate(Histories.of("PFPF")).verdict()).isEqualTo(Verdict.NONE);
        assertThat(strategy.evaluate(Histories.of("PFPFP")).verdict()).isEqualTo(Verdict.FLAKY);
    }

    @Test
    void needsMinimumFlipsEvenWhenTheRateIsHigh() {
        // 2 flips in 5 runs is a rate of 0.5, but still only a regression and its fix
        assertThat(strategy.evaluate(Histories.of("PFFFP")).verdict()).isEqualTo(Verdict.NONE);
    }

    @Test
    void ignoresAFewFlipsSpreadOverALongWindow() {
        FlipRateStrategy longWindow = new FlipRateStrategy(new DetectionSettings(41, 5, 3, 0.15, 0.3, 10));
        // 3 flips in 40 pairs: 0.075, below the suspicious rate although the minimum number of flips is reached
        String outcomes = "P".repeat(37) + "FPFP";

        assertThat(longWindow.evaluate(Histories.of(outcomes))).isEqualTo(FlakinessSignal.NONE);
    }

    @Test
    void onlyLooksAtTheLatestWindow() {
        // the flips happened before the last 21 runs
        String oldFlips = "PFPFPFPF";
        String recentStable = "P".repeat(21);

        assertThat(strategy.evaluate(Histories.of(oldFlips + recentStable)).verdict()).isEqualTo(Verdict.NONE);
    }

    @Test
    void ignoresSkippedRunsWhenCountingFlips() {
        assertThat(FlipRateStrategy.countFlips(Histories.of("PSFSP").ran())).isEqualTo(2);
    }

    @Test
    void explainsTheEvidence() {
        assertThat(strategy.evaluate(Histories.of("PPPPPPPPPPPPPPFPFPFPP")).reason())
                .isEqualTo("Switched between pass and fail 6 times in the last 21 runs (flip rate 0.30)");
    }
}
