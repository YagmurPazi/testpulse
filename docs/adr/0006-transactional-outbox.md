# 0006. Transactional outbox for publishing events

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

When ingestion-service stores a test run, it must also publish a `TestRunIngested` event
([ADR-0004](0004-kafka-as-event-backbone.md)). Writing to PostgreSQL and publishing to Kafka are two operations on two
systems, and there is no transaction that spans both (the *dual write* problem):

- Store, then publish: if the service crashes or Kafka is down in between, the run exists but no consumer ever hears
  about it.
- Publish, then store: if the database write fails, consumers process a run that does not exist.

How do we guarantee that every stored run is eventually published, and nothing else is?

## Considered options

1. Store and publish directly, accept occasional loss
2. Kafka transactions
3. Transactional outbox with a polling relay
4. Transactional outbox with change data capture (Debezium)

## Decision outcome

Chosen option: **3, transactional outbox with a polling relay.**

1. The use case writes the run, its results and an `outbox_event` row in **one database transaction**. Either all
   three are committed or none (tested by rolling back on a failing outbox write).
2. A scheduled relay reads unpublished rows in creation order, sends them to Kafka, waits for the acknowledgements and
   marks the rows as published, all in one database transaction.
3. If sending fails, the transaction rolls back; the rows stay unpublished and are sent again on the next run.

Why not option 2: Kafka transactions make several Kafka writes atomic, but they cannot include a PostgreSQL write.
Why not option 4: Debezium reads the database's write-ahead log and needs Kafka Connect, another runtime to operate.
It is the better choice at scale, but too much infrastructure for this project.

### Relay design

- **One relay at a time.** A transaction-scoped PostgreSQL advisory lock (`pg_try_advisory_xact_lock`) lets only one
  instance relay; others skip the round. The alternative, `SELECT ... FOR UPDATE SKIP LOCKED`, lets instances work in
  parallel but can publish two events of the same repository out of order, which would break per-repository ordering.
- **Producer settings:** `acks=all` (acknowledged only when all in-sync replicas have the event),
  `enable.idempotence=true` (producer retries do not duplicate or reorder within a partition), `zstd` compression.
- **Batches** are sent together and then awaited, so the producer can pipeline them.

### Consequences

- Good: no lost events and no events for data that was never stored, even when Kafka is down; events wait in the
  outbox until Kafka is back.
- Good: the use case needs no knowledge of Kafka; publishing is an outbound port implemented by the outbox.
- Bad: **at-least-once delivery.** If the relay crashes after Kafka acknowledged a batch but before the database commit,
  the batch is sent again. Every event carries an `eventId` (also as a Kafka header) and consumers must deduplicate.
- Bad: **latency.** Events reach Kafka after the next relay round (1 second by default), not immediately.
- Bad: the relay holds a database transaction while waiting for Kafka (bounded by the send timeout).
- Bad: a single active relay limits throughput. If that ever matters, ordering can be kept per key with parallel
  relays partitioned by key, or by moving to CDC.
- Follow-up: published rows are kept for inspection; a cleanup job that deletes old published rows is needed before
  the table grows large.
