package io.github.yagmurpazi.testpulse.notification.domain;

import static io.github.yagmurpazi.testpulse.notification.domain.StatusChanges.change;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StatusChangeTest {

    @Test
    void labelsTheTestWithItsSimpleClassName() {
        assertThat(change(TestStatus.SUSPICIOUS, TestStatus.FLAKY).testLabel())
                .isEqualTo("CheckoutServiceTest.appliesDiscount");
    }

    @Test
    void rejectsAChangeToTheSameStatus() {
        assertThatThrownBy(() -> change(TestStatus.FLAKY, TestStatus.FLAKY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
