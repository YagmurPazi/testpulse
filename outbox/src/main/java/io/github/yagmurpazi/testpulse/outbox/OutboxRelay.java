package io.github.yagmurpazi.testpulse.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
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
        int relayed;
        do {
            relayed = relayBatch();
            total += relayed;
        } while (relayed == batchSize);
        if (total > 0) {
            LOG.info("Published {} outbox event(s) to Kafka", total);
        }
        return total;
    }

    private int relayBatch() {
        Integer relayed = transaction.execute(status -> {
            if (!acquireRelayLock()) {
                return 0;
            }
            List<OutboxEvent> events = jdbc.sql("""
                            SELECT id, topic, message_key, payload
                              FROM outbox_event
                             WHERE published_at IS NULL
                             ORDER BY created_at, id
                             LIMIT :limit
                            """)
                    .param("limit", batchSize)
                    .query(OutboxEvent.class)
                    .list();
            if (events.isEmpty()) {
                return 0;
            }
            sendAndAwait(events);
            jdbc.sql("UPDATE outbox_event SET published_at = now() WHERE id IN (:ids)")
                    .param("ids", events.stream().map(OutboxEvent::id).toList())
                    .update();
            return events.size();
        });
        return relayed == null ? 0 : relayed;
    }

    /** Transaction-scoped: released automatically on commit or rollback. */
    private boolean acquireRelayLock() {
        return Boolean.TRUE.equals(jdbc.sql("SELECT pg_try_advisory_xact_lock(:lockId)")
                .param("lockId", RELAY_LOCK_ID)
                .query(Boolean.class)
                .single());
    }

    /** Sends the whole batch first so the producer can pipeline it, then waits for every acknowledgement. */
    private void sendAndAwait(List<OutboxEvent> events) {
        List<CompletableFuture<SendResult<String, String>>> acknowledgements = new ArrayList<>();
        try {
            for (OutboxEvent event : events) {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(event.topic(), event.messageKey(), event.payload());
                record.headers().add(EVENT_ID_HEADER, event.id().toString().getBytes(StandardCharsets.UTF_8));
                acknowledgements.add(kafka.send(record));
            }
        } catch (KafkaException | org.springframework.kafka.KafkaException e) {
            // e.g. no broker metadata within max.block.ms: the producer fails before returning a future.
            // The client library and Spring Kafka use two unrelated exception types for this.
            throw new OutboxRelayException("Kafka rejected the batch", e);
        }
        try {
            CompletableFuture.allOf(acknowledgements.toArray(CompletableFuture[]::new))
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxRelayException("Interrupted while waiting for Kafka", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new OutboxRelayException("Kafka did not acknowledge " + events.size() + " event(s)", e);
        }
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
