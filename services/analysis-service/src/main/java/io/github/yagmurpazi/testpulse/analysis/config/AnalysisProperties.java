package io.github.yagmurpazi.testpulse.analysis.config;

import io.github.yagmurpazi.testpulse.analysis.domain.DetectionSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under {@code testpulse.analysis}; defaults equal {@link DetectionSettings#DEFAULTS}. */
@ConfigurationProperties("testpulse.analysis")
record AnalysisProperties(@DefaultValue Detection detection) {

    record Detection(
            @DefaultValue("20") int window,
            @DefaultValue("5") int minSamples,
            @DefaultValue("3") int minFlips,
            @DefaultValue("0.15") double suspiciousFlipRate,
            @DefaultValue("0.3") double flakyFlipRate,
            @DefaultValue("10") int recoveryRuns) {

        DetectionSettings toSettings() {
            return new DetectionSettings(window, minSamples, minFlips, suspiciousFlipRate, flakyFlipRate,
                    recoveryRuns);
        }
    }
}
