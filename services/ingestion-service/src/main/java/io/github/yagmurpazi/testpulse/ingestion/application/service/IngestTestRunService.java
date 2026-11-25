package io.github.yagmurpazi.testpulse.ingestion.application.service;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestionResult;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunEventPublisher;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunRepository;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Idempotent ingestion of test runs.
 *
 * <p>Framework free: the transaction around {@link #ingest} is applied by configuration, not by an annotation here
 * (see ADR-0003).
 */
public final class IngestTestRunService implements IngestTestRunUseCase {

    private final TestRunRepository repository;
    private final TestRunEventPublisher eventPublisher;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public IngestTestRunService(TestRunRepository repository, TestRunEventPublisher eventPublisher, Clock clock,
            Supplier<UUID> idGenerator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    @Override
    public IngestionResult ingest(IngestTestRunCommand command) {
        Optional<TestRun> existing = repository.findByIdentity(command.identity());
        if (existing.isPresent()) {
            return IngestionResult.alreadyIngested(existing.get());
        }

        TestRun run = new TestRun(idGenerator.get(), command.identity(), command.commitSha(), command.branch(),
                clock.instant(), command.results());

        // A concurrent request with the same identity may have stored it between the lookup and this point.
        if (!repository.saveIfAbsent(run)) {
            TestRun winner = repository.findByIdentity(command.identity())
                    .orElseThrow(() -> new IllegalStateException("run vanished after conflict: " + run.identity()));
            return IngestionResult.alreadyIngested(winner);
        }

        eventPublisher.publishIngested(run);
        return IngestionResult.created(run);
    }
}
