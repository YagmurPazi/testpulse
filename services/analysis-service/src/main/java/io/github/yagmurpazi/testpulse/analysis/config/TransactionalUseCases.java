package io.github.yagmurpazi.testpulse.analysis.config;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.ChangeTestStatusUseCase;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunCommand;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunUseCase;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessingResult;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import java.util.UUID;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Decorators that run each use case call in one database transaction, so the framework-free application layer
 * needs no {@code @Transactional} (ADR-0003).
 */
final class TransactionalUseCases {

    private TransactionalUseCases() {
    }

    static ProcessTestRunUseCase processTestRun(ProcessTestRunUseCase delegate, TransactionOperations transaction) {
        return new ProcessTestRunUseCase() {
            @Override
            public ProcessingResult process(ProcessTestRunCommand command) {
                return transaction.execute(status -> delegate.process(command));
            }
        };
    }

    static ChangeTestStatusUseCase changeTestStatus(ChangeTestStatusUseCase delegate,
            TransactionOperations transaction) {
        return new ChangeTestStatusUseCase() {
            @Override
            public StatusChange quarantine(UUID testId) {
                return transaction.execute(status -> delegate.quarantine(testId));
            }

            @Override
            public StatusChange release(UUID testId) {
                return transaction.execute(status -> delegate.release(testId));
            }
        };
    }
}
