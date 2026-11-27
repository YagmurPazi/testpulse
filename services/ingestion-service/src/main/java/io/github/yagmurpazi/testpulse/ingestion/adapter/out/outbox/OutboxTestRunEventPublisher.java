package io.github.yagmurpazi.testpulse.ingestion.adapter.out.outbox;

import io.github.yagmurpazi.testpulse.events.TestRunIngested;
import io.github.yagmurpazi.testpulse.events.Topics;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.TestRunEventPublisher;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestRun;
import io.github.yagmurpazi.testpulse.outbox.OutboxWriter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Publishes through the transactional outbox (ADR-0006). Runs inside the caller's transaction, so the event is
 * stored if and only if the run is stored; the outbox relay delivers it to Kafka afterwards.
 */
@Component
class OutboxTestRunEventPublisher implements TestRunEventPublisher {

    private final OutboxWriter outbox;

    OutboxTestRunEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /** A large run becomes several events; all of them are stored in the same transaction as the run. */
    @Override
    public void publishIngested(TestRun run) {
        for (TestRunIngested event : TestRunIngestedMapper.toEvents(run, UUID::randomUUID)) {
            outbox.append(event.eventId(), Topics.TEST_RUNS, event.repository(), event, event.occurredAt());
        }
    }
}
