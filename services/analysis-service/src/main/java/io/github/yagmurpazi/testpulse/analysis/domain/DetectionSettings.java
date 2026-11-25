package io.github.yagmurpazi.testpulse.analysis.domain;

/**
 * Tuning of flakiness detection. Defaults and their reasoning are documented in ADR-0007.
 *
 * @param window             number of recent executions considered by the flip rate
 * @param minSamples         executions needed before the flip rate is evaluated at all
 * @param minFlips           pass/fail switches needed before the flip rate counts; a fixed regression switches twice
 * @param suspiciousFlipRate flip rate from which a test is suspicious
 * @param flakyFlipRate      flip rate from which a test is flaky
 * @param recoveryRuns       clean passes in a row after which a suspicious or flaky test is stable again
 */
public record DetectionSettings(
        int window,
        int minSamples,
        int minFlips,
        double suspiciousFlipRate,
        double flakyFlipRate,
        int recoveryRuns) {

    public static final DetectionSettings DEFAULTS = new DetectionSettings(20, 5, 3, 0.15, 0.3, 10);

    public DetectionSettings {
        if (minSamples < 2 || window < minSamples) {
            throw new IllegalArgumentException("need 2 <= minSamples <= window");
        }
        if (minFlips < 1) {
            throw new IllegalArgumentException("minFlips must be positive");
        }
        if (suspiciousFlipRate <= 0 || flakyFlipRate < suspiciousFlipRate || flakyFlipRate > 1) {
            throw new IllegalArgumentException("need 0 < suspiciousFlipRate <= flakyFlipRate <= 1");
        }
        if (recoveryRuns < 1) {
            throw new IllegalArgumentException("recoveryRuns must be positive");
        }
    }
}
