package io.github.yagmurpazi.testpulse.notification.domain;

import static io.github.yagmurpazi.testpulse.notification.domain.StatusChanges.NOW;
import static io.github.yagmurpazi.testpulse.notification.domain.StatusChanges.change;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NotificationPolicyTest {

    private final NotificationPolicy policy = new NotificationPolicy(Duration.ofHours(24));

    /** Every transition the lifecycle allows (ADR-0007), plus the ones it does not, for robustness. */
    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
        "STABLE,      SUSPICIOUS,  NONE",
        "SUSPICIOUS,  STABLE,      NONE",
        "SUSPICIOUS,  FLAKY,       BECAME_FLAKY",
        "STABLE,      FLAKY,       BECAME_FLAKY",
        "FLAKY,       STABLE,      RECOVERED",
        "FLAKY,       QUARANTINED, QUARANTINED",
        "QUARANTINED, SUSPICIOUS,  RELEASED",
        "QUARANTINED, STABLE,      RELEASED",
        "FLAKY,       SUSPICIOUS,  NONE",
        "STABLE,      QUARANTINED, QUARANTINED"
    })
    void notifiesOnlyChangesThatNeedAttentionOrRecordADecision(TestStatus from, TestStatus to, String expected) {
        Optional<NotificationKind> kind = policy.kindOf(change(from, to));

        assertThat(kind.map(Enum::name).orElse("NONE")).isEqualTo(expected);
    }

    @Test
    void changesOlderThanTheMaximumAgeAreStale() {
        assertThat(policy.isStale(change(TestStatus.SUSPICIOUS, TestStatus.FLAKY, NOW.minus(Duration.ofHours(25))),
                NOW)).isTrue();
        assertThat(policy.isStale(change(TestStatus.SUSPICIOUS, TestStatus.FLAKY, NOW.minus(Duration.ofHours(24))),
                NOW)).isFalse();
        assertThat(policy.isStale(change(TestStatus.SUSPICIOUS, TestStatus.FLAKY, NOW), NOW)).isFalse();
    }

    @Test
    void requiresAPositiveMaximumAge() {
        assertThatThrownBy(() -> new NotificationPolicy(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationPolicy(Duration.ofHours(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
