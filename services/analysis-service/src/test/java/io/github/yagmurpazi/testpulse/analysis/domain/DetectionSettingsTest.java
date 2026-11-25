package io.github.yagmurpazi.testpulse.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DetectionSettingsTest {

    @Test
    void defaultsAreValid() {
        assertThat(DetectionSettings.DEFAULTS.window()).isEqualTo(20);
        assertThat(DetectionSettings.DEFAULTS.minFlips()).isEqualTo(3);
    }

    @Test
    void acceptsBoundaryValues() {
        assertThat(new DetectionSettings(2, 2, 1, 1.0, 1.0, 1).flakyFlipRate()).isEqualTo(1.0);
    }

    @ParameterizedTest
    @CsvSource({
        "20, 1, 3, 0.15, 0.30, 10",
        "4, 5, 3, 0.15, 0.30, 10",
        "20, 5, 0, 0.15, 0.30, 10",
        "20, 5, 3, 0.00, 0.30, 10",
        "20, 5, 3, 0.30, 0.15, 10",
        "20, 5, 3, 0.15, 1.01, 10",
        "20, 5, 3, 0.15, 0.30, 0"
    })
    void rejectsInconsistentSettings(int window, int minSamples, int minFlips, double suspicious, double flaky,
            int recovery) {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new DetectionSettings(window, minSamples, minFlips, suspicious, flaky, recovery));
    }
}
