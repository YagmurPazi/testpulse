package io.github.yagmurpazi.testpulse.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RunIdentityTest {

    @Test
    void differentAttemptsAreDifferentRuns() {
        assertThat(new RunIdentity("acme/shop", "42", 1)).isNotEqualTo(new RunIdentity("acme/shop", "42", 2));
    }

    @Test
    void sameValuesAreTheSameRun() {
        assertThat(new RunIdentity("acme/shop", "42", 1)).isEqualTo(new RunIdentity("acme/shop", "42", 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"shop", "acme/", "/shop", "acme/shop/extra", "acme shop/x", ""})
    void rejectsRepositoryNotInOwnerNameForm(String repository) {
        assertThatIllegalArgumentException().isThrownBy(() -> new RunIdentity(repository, "42", 1));
    }

    @Test
    void acceptsTypicalRepositoryNames() {
        assertThat(new RunIdentity("spring-projects/spring.boot_x", "42", 1).repository())
                .isEqualTo("spring-projects/spring.boot_x");
    }

    @Test
    void rejectsAttemptBelowOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RunIdentity("acme/shop", "42", 0));
    }

    @Test
    void rejectsBlankExternalRunId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RunIdentity("acme/shop", " ", 1));
    }

    @Test
    void acceptsExternalRunIdUpToLimitAndRejectsLonger() {
        assertThat(new RunIdentity("acme/shop", "x".repeat(128), 1).externalRunId()).hasSize(128);
        assertThatIllegalArgumentException().isThrownBy(() -> new RunIdentity("acme/shop", "x".repeat(129), 1));
    }
}
