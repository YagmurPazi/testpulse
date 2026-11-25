package io.github.yagmurpazi.testpulse.analysis.domain;

/**
 * One way of recognising a flaky test from its history (Strategy pattern). New detection methods, such as a
 * duration anomaly, are added as new implementations without touching the existing ones.
 */
public interface FlakinessStrategy {

    FlakinessSignal evaluate(TestHistory history);
}
