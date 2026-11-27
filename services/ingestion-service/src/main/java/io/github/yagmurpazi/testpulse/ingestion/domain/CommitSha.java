package io.github.yagmurpazi.testpulse.ingestion.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** A git commit hash: SHA-1 (40 hex characters) or SHA-256 (64), abbreviated forms down to 7 characters. */
public record CommitSha(String value) {

    private static final Pattern HEX = Pattern.compile("[0-9a-f]{7,64}");

    public CommitSha {
        Objects.requireNonNull(value, "value");
        value = value.toLowerCase(Locale.ROOT);
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("not a commit hash: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
