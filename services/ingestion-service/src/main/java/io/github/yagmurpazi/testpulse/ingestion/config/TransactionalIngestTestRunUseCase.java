package io.github.yagmurpazi.testpulse.ingestion.config;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunCommand;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestTestRunUseCase;
import io.github.yagmurpazi.testpulse.ingestion.application.port.in.IngestionResult;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Runs the use case in one database transaction: the run, its results and its outbox event are committed together
 * or not at all. A decorator instead of {@code @Transactional}, so the application layer stays framework free.
 */
final class TransactionalIngestTestRunUseCase implements IngestTestRunUseCase {

    private final IngestTestRunUseCase delegate;
    private final TransactionOperations transaction;

    TransactionalIngestTestRunUseCase(IngestTestRunUseCase delegate, TransactionOperations transaction) {
        this.delegate = delegate;
        this.transaction = transaction;
    }

    @Override
    public IngestionResult ingest(IngestTestRunCommand command) {
        return transaction.execute(status -> delegate.ingest(command));
    }
}
