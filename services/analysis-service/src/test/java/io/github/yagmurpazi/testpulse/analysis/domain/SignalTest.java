package io.github.yagmurpazi.testpulse.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class SignalTest {

    @Test
    void verdictsAreOrderedByStrength() {
        assertThat(Verdict.FLAKY.isStrongerThan(Verdict.SUSPICIOUS)).isTrue();
        assertThat(Verdict.SUSPICIOUS.isStrongerThan(Verdict.NONE)).isTrue();
        assertThat(Verdict.SUSPICIOUS.isStrongerThan(Verdict.SUSPICIOUS)).isFalse();
        assertThat(Verdict.NONE.isStrongerThan(Verdict.FLAKY)).isFalse();
    }

    @Test
    void signalOtherThanNoneNeedsAReason() {
        assertThatIllegalArgumentException().isThrownBy(() -> FlakinessSignal.flaky(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> FlakinessSignal.suspicious(""));
        assertThat(FlakinessSignal.NONE.reason()).isEmpty();
    }
}
