package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.List;
import java.util.Locale;

/**
 * Signal from instability over time: how often the result switches between pass and fail in the recent executions.
 *
 * <p>A real regression that is fixed later switches exactly twice (pass, fail, ..., fail, pass). That is not flaky,
 * so a minimum number of switches is required before the rate counts. A test that keeps failing never switches and
 * is broken, not flaky.
 */
public final class FlipRateStrategy implements FlakinessStrategy {

    private final DetectionSettings settings;

    public FlipRateStrategy(DetectionSettings settings) {
        this.settings = settings;
    }

    @Override
    public FlakinessSignal evaluate(TestHistory history) {
        List<TestExecution> ran = history.ran();
        List<TestExecution> window = ran.subList(Math.max(0, ran.size() - settings.window()), ran.size());
        if (window.size() < settings.minSamples()) {
            return FlakinessSignal.NONE;
        }
        int flips = countFlips(window);
        if (flips < settings.minFlips()) {
            return FlakinessSignal.NONE;
        }
        double rate = (double) flips / (window.size() - 1);
        String reason = String.format(Locale.ROOT, "Switched between pass and fail %d times in the last %d runs"
                + " (flip rate %.2f)", flips, window.size(), rate);
        if (rate >= settings.flakyFlipRate()) {
            return FlakinessSignal.flaky(reason);
        }
        if (rate >= settings.suspiciousFlipRate()) {
            return FlakinessSignal.suspicious(reason);
        }
        return FlakinessSignal.NONE;
    }

    static int countFlips(List<TestExecution> executions) {
        int flips = 0;
        for (int i = 1; i < executions.size(); i++) {
            if (executions.get(i).outcome().isFailure() != executions.get(i - 1).outcome().isFailure()) {
                flips++;
            }
        }
        return flips;
    }
}
