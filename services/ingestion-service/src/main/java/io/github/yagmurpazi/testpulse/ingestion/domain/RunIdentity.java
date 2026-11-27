package io.github.yagmurpazi.testpulse.ingestion.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifies a CI run attempt. Two uploads with the same identity are the same run (idempotent ingestion).
 *
 * <p>The attempt is part of the identity on purpose: re-running a CI job on the same commit produces a new attempt,
 * and a different result on a re-run is exactly the signal flakiness detection looks for.
 *
 * @param repository    repository in {@code owner/name} form
 * @param externalRunId run identifier in the CI system, or a content hash for manual uploads
 * @param attempt       attempt number, starting at 1
 */
public record RunIdentity(String repository, String externalRunId, int attempt) {

    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final int MAX_EXTERNAL_RUN_ID_LENGTH = 128;

    public RunIdentity {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(externalRunId, "externalRunId");
        if (!REPOSITORY.matcher(repository).matches()) {
            throw new IllegalArgumentException("repository must have the form owner/name: " + repository);
        }
        if (externalRunId.isBlank() || externalRunId.length() > MAX_EXTERNAL_RUN_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "externalRunId must be 1 to " + MAX_EXTERNAL_RUN_ID_LENGTH + " characters");
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }
    }
}
