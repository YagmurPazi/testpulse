package io.github.yagmurpazi.testpulse.ingestion.application.port.out;

import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;

/**
 * Announces stored runs to other services.
 *
 * <p>Called inside the same unit of work as {@link TestRunRepository#saveIfAbsent}: an implementation must either
 * take part in that transaction (transactional outbox) or accept that events can be lost.
 */
public interface TestRunEventPublisher {

    void publishIngested(TestRun run);
}
