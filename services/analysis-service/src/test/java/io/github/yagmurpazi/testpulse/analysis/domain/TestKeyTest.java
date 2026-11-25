package io.github.yagmurpazi.testpulse.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TestKeyTest {

    @Test
    void displayNameUsesSimpleClassName() {
        assertThat(Histories.KEY.displayName()).isEqualTo("CheckoutServiceTest.appliesDiscount");
    }

    @Test
    void displayNameKeepsClassWithoutPackage() {
        assertThat(new TestKey("acme/shop", "CartTest", "adds").displayName()).isEqualTo("CartTest.adds");
    }

    @Test
    void rejectsBlankParts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TestKey(" ", "C", "t"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TestKey("acme/shop", "", "t"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TestKey("acme/shop", "C", " "));
    }
}
