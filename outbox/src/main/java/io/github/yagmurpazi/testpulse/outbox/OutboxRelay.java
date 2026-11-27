package io.github.yagmurpazi.testpulse.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Moves events from the outbox table to Kafka.
 *
 * <p>Each batch runs in one database transaction: read unpublished events, send them, wait for Kafka's
 * acknowledgements, mark them published. If anything fails, the transaction rolls back and the events are sent again
 * in a later batch. Delivery is therefore <b>at least once</b>, and consumers deduplicate on the event id.
 *
 * <p>Only one relay runs at a time, guarded by a PostgreSQL advisory lock. Parallel relays (for example with
 * {@code SKIP LOCKED}) would be faster but could publish two events of the same repository out of order.
 *
 * <p>An event Kafka can never accept (larger than its message limit) is marked <i>rejected</i>, with the reason, and
 * logged as an error; the events behind it are published. Retrying it would only repeat the rejection and hold back
 * every later event, as the relay publishes in order. Every other failure is retried.
 */
public final class OutboxRelay {

    /** Header with the event id, so consumers can deduplicate without parsing the payload. */
    public static final String EVENT_ID_HEADER = "eventId";

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    /** Arbitrary constant identifying the relay's advisory lock. */
    private static final long RELAY_LOCK_ID = 0x7E57_0B0BL;

    private final JdbcClient jdbc;
    private final TransactionOperations transaction;
    private final KafkaOperations<String, String> kafka;
    private final int batchSize;
    private final Duration sendTimeout;

    public OutboxRelay(JdbcClient jdbc, TransactionOperations transaction, KafkaOperations<String, String> kafka,
            int batchSize, Duration sendTimeout) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.kafka = kafka;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    /**
     * Publishes all pending events, batch by batch.
     *
     * @return number of events published
     */
    public int relayPending() {
        int total = 0;
        BatchResult batch;
        do {
            batch = relayBatch();
            total += batch.published();
        } while (batch.handled() == batchSize);
        if (total > 0) {
            LOG.info("Published {} outbox event(s) to Kafka", total);
        }
        return total;
    }

    private BatchResult relayBatch() {
        BatchResult result = transaction.execute(status -> {
            if (!acquireRelayLock()) {
                return BatchResult.NONE;
            }
            List<OutboxEvent> events = jdbc.sql("""
                            SELECT id, topic, message_key, payload
                              FROM outbox_event
                             WHERE published_at IS NULL AND rejected_at IS NULL
                             ORDER BY created_at, id
                             LIMIT :limit
                            """)
                    .param("limit", batchSize)
                    .query(OutboxEvent.class)
                    .list();
            if (events.isEmpty()) {
                return BatchResult.NONE;
            }
            Map<UUID, String> rejected = sendAndAwait(events);
            List<UUID> published = events.stream().map(OutboxEvent::id).filter(id -> !rejected.containsKey(id))
                    .toList();
            if (!published.isEmpty()) {
                jdbc.sql("UPDATE outbox_event SET published_at = now() WHERE id IN (:ids)")
                        .param("ids", published)
                        .update();
            }
            events.stream().filter(event -> rejected.containsKey(event.id())).forEach(event -> reject(event,
                    rejected.get(event.id())));
            return new BatchResult(events.size(), published.size());
        });
        return result == null ? BatchResult.NONE : result;
    }

    private void reject(OutboxEvent event, String reason) {
        jdbc.sql("UPDATE outbox_event SET rejected_at = now(), rejection = :reason WHERE id = :id")
                .param("reason", reason)
                .param("id", event.id())
                .update();
        LOG.error("Kafka can never accept outbox event {} for {} (key {}), marked rejected: {}", event.id(),
                event.topic(), event.messageKey(), reason);
    }

    /** Transaction-scoped: released automatically on commit or rollback. */
    private boolean acquireRelayLock() {
        return Boolean.TRUE.equals(jdbc.sql("SELECT pg_try_advisory_xact_lock(:lockId)")
                .param("lockId", RELAY_LOCK_ID)
                .query(Boolean.class)
                .single());
    }

    /**
     * Sends the whole batch first so the producer can pipeline it, then waits for every acknowledgement.
     *
     * @return the events Kafka can never accept, with the reason; empty if all were acknowledged
     * @throws OutboxRelayException on any other failure, so the whole batch is retried
     */
    private Map<UUID, String> sendAndAwait(List<OutboxEvent> events) {
        List<CompletableFuture<SendResult<String, String>>> acknowledgements = new ArrayList<>();
        for (OutboxEvent event : events) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(event.topic(), event.messageKey(), event.payload());
            record.headers().add(EVENT_ID_HEADER, event.id().toString().getBytes(StandardCharsets.UTF_8));
            try {
                acknowledgements.add(kafka.send(record));
            } catch (KafkaException | org.springframework.kafka.KafkaException e) {
                // e.g. no broker metadata within max.block.ms: the producer fails before returning a future.
                // The client library and Spring Kafka use two unrelated exception types for this.
                if (!isPermanent(e)) {
                    throw new OutboxRelayException("Kafka rejected the batch", e);
                }
                acknowledgements.add(CompletableFuture.failedFuture(e));
            }
        }
        try {
            CompletableFuture.allOf(acknowledgements.toArray(CompletableFuture[]::new))
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxRelayException("Interrupted while waiting for Kafka", e);
        } catch (TimeoutException e) {
            throw new OutboxRelayException("Kafka did not acknowledge " + events.size() + " event(s)", e);
        } catch (ExecutionException e) {
            // at least one send failed; every future is complete now and classified below
        }
        Map<UUID, String> rejected = new LinkedHashMap<>();
        for (int i = 0; i < events.size(); i++) {
            CompletableFuture<SendResult<String, String>> acknowledgement = acknowledgements.get(i);
            if (acknowledgement.isCompletedExceptionally()) {
                Throwable failure = acknowledgement.exceptionNow();
                if (!isPermanent(failure)) {
                    throw new OutboxRelayException("Kafka did not acknowledge event " + events.get(i).id(), failure);
                }
                rejected.put(events.get(i).id(), mostSpecificMessage(failure));
            }
        }
        return rejected;
    }

    /** Failures that sending the same record again can never fix. */
    static boolean isPermanent(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RecordTooLargeException) {
                return true;
            }
        }
        return false;
    }

    private static String mostSpecificMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private record BatchResult(int handled, int published) {

        static final BatchResult NONE = new BatchResult(0, 0);
    }

    record OutboxEvent(UUID id, String topic, String messageKey, String payload) {
    }

    /** Publishing failed; the events stay in the outbox and are retried. */
    public static final class OutboxRelayException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        OutboxRelayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
