package io.github.yagmurpazi.testpulse.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Published by ingestion-service once per newly stored test run, on topic {@link Topics#TEST_RUNS}.
 *
 * <p>The event carries every test result of the run (event-carried state transfer), so consumers never need to
 * call ingestion-service back, and replaying the topic is enough to rebuild their state.
 *
 * <p>Kafka record key: {@link #repository()}, so all runs of a repository are consumed in order.
 *
 * <p>Delivery is at least once: consumers must deduplicate on {@link #eventId()}.
 *
 * <p>A large run is split into several events ({@link #part()} of {@link #parts()}), because Kafka limits the size of
 * a message. Each part carries the run's metadata and a share of its results; all results of one test are in the same
 * part. Parts have their own event ids and can be processed independently: every test is evaluated on its own.
 *
 * @param eventId       unique per event; the idempotency key for consumers
 * @param schemaVersion version of this contract, see {@link #SCHEMA_VERSION}
 * @param occurredAt    when the run was stored by ingestion-service
 * @param runId         TestPulse identifier of the run
 * @param repository    repository in {@code owner/name} form
 * @param commitSha     commit the tests ran against
 * @param branch        branch name
 * @param externalRunId identifier of the run in the CI system, or a content hash for manual uploads
 * @param attempt       CI attempt number, starting at 1; a re-run of the same CI run increments it
 * @param results       the test case results of the run, or of this part of it
 * @param part          number of this part, from 1; 1 in events written before parts existed
 * @param parts         number of parts of the run; 1 in events written before parts existed
 */
public record TestRunIngested(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID runId,
        String repository,
        String commitSha,
        String branch,
        String externalRunId,
        int attempt,
        List<TestCaseResult> results,
        Integer part,
        Integer parts) {

    /**
     * Current contract version. Additive, optional fields keep the version; renaming, removing or changing the
     * meaning of a field requires a new version (see ADR-0005).
     */
    public static final int SCHEMA_VERSION = 1;

    public TestRunIngested {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(externalRunId, "externalRunId");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }
        results = List.copyOf(results);
        if (part == null && parts == null) {
            // written before parts existed: the whole run in one event. Boxed types, because a missing primitive
            // would fail deserialization and make every older event unreadable.
            part = 1;
            parts = 1;
        }
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(parts, "parts");
        if (part < 1 || part > parts) {
            throw new IllegalArgumentException("part must be between 1 and parts, was " + part + " of " + parts);
        }
    }

    /** The whole run in one event. */
    public TestRunIngested(UUID eventId, int schemaVersion, Instant occurredAt, UUID runId, String repository,
            String commitSha, String branch, String externalRunId, int attempt, List<TestCaseResult> results) {
        this(eventId, schemaVersion, occurredAt, runId, repository, commitSha, branch, externalRunId, attempt,
                results, 1, 1);
    }
}
