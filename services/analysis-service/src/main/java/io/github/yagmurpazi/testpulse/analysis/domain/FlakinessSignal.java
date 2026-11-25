package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.Objects;

/**
 * Result of evaluating a test's history.
 *
 * @param verdict how strong the evidence is
 * @param reason  human-readable explanation of the evidence; empty for {@link Verdict#NONE}
 */
public record FlakinessSignal(Verdict verdict, String reason) {

    public static final FlakinessSignal NONE = new FlakinessSignal(Verdict.NONE, "");

    public FlakinessSignal {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reason, "reason");
        if (verdict != Verdict.NONE && reason.isBlank()) {
            throw new IllegalArgumentException("a signal needs a reason");
        }
    }

    public static FlakinessSignal suspicious(String reason) {
        return new FlakinessSignal(Verdict.SUSPICIOUS, reason);
    }

    public static FlakinessSignal flaky(String reason) {
        return new FlakinessSignal(Verdict.FLAKY, reason);
    }
}
