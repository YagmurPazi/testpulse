package io.github.yagmurpazi.testpulse.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommitShaTest {

    @ParameterizedTest
    @ValueSource(ints = {7, 40, 64})
    void acceptsAbbreviatedSha1AndSha256Lengths(int length) {
        assertThat(new CommitSha("a".repeat(length)).value()).hasSize(length);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc123", "g000000", "", "abc 1234"})
    void rejectsValuesThatAreNotCommitHashes(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new CommitSha(value));
    }

    @Test
    void rejectsValuesLongerThanSha256() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CommitSha("a".repeat(65)));
    }

    @Test
    void normalisesToLowerCase() {
        assertThat(new CommitSha("ABCDEF1")).isEqualTo(new CommitSha("abcdef1"));
        assertThat(new CommitSha("ABCDEF1")).hasToString("abcdef1");
    }
}
