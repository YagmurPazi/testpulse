package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.List;

/** Runs every strategy and keeps the strongest signal; on a tie, the strategy listed first wins. */
public final class FlakinessDetector {

    private final List<FlakinessStrategy> strategies;

    public FlakinessDetector(List<FlakinessStrategy> strategies) {
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("at least one strategy is required");
        }
        this.strategies = List.copyOf(strategies);
    }

    /** The standard detector: same-commit conflicts first, then flip rate. */
    public static FlakinessDetector withDefaultStrategies(DetectionSettings settings) {
        return new FlakinessDetector(List.of(new SameCommitConflictStrategy(), new FlipRateStrategy(settings)));
    }

    public FlakinessSignal evaluate(TestHistory history) {
        FlakinessSignal strongest = FlakinessSignal.NONE;
        for (FlakinessStrategy strategy : strategies) {
            FlakinessSignal signal = strategy.evaluate(history);
            if (signal.verdict().isStrongerThan(strongest.verdict())) {
                strongest = signal;
            }
        }
        return strongest;
    }
}
