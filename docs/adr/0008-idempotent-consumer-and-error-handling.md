# 0008. Idempotent event consumption and error handling

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

analysis-service consumes `TestRunIngested` events. Two facts shape the consumer:

- **Delivery is at least once** ([ADR-0006](0006-transactional-outbox.md)): the relay can publish an event twice, and
  a consumer that crashes after processing but before committing its offset receives the event again.
- **Some records can never be processed** (unreadable JSON, an unknown schema version, invalid content). Kafka
  delivers a partition in order, so retrying such a record forever blocks every record behind it (a *poison message*).

How do we process each event exactly once in effect, and what happens on errors?

## Considered options

Deduplication:

1. Rely on Kafka's exactly-once semantics (transactions)
2. Make every write naturally idempotent (upserts)
3. Record processed event ids in the same database transaction as the effects (idempotent consumer, "inbox")

Error handling:

- A. Retry every error a fixed number of times, then skip
- B. Classify: skip permanent errors at once, retry transient errors without limit
- C. Send failed records to a dead letter topic

## Decision outcome

Chosen options: **3, a processed-event table**, and **B, classified errors.**

### Deduplication

- `processed_event(event_id)` is inserted with `ON CONFLICT DO NOTHING` at the start of the processing transaction. If
  the row already exists, the event is acknowledged without any effect.
- The marker, the new executions, the status changes and their outbox events commit together. If processing fails,
  the marker is rolled back too, so the redelivered event is processed again (tested).
- Offsets are committed by the listener container after the listener returns, never before.

Why not option 1: Kafka transactions give exactly-once only for Kafka-to-Kafka flows; our effects are in PostgreSQL.
Why not option 2: appending executions and recording status changes are not naturally idempotent.

### Error handling

| Error | Example | Handling |
|---|---|---|
| Permanent (`InvalidEventException`) | unreadable JSON, missing fields, schema version ≠ 1, blank test names | logged and skipped at once |
| Everything else | database unavailable, unexpected bug | exponential backoff (1 s, doubling, at most 30 s), retried without limit |

Validation the contract does not do (such as blank test names) happens in the messaging adapter, so it fails as a
permanent error and not inside the use case, where it would look transient and be retried forever.

Retrying transient errors forever blocks the partition on purpose: skipping would lose data and break the
per-repository order, while waiting resumes processing as soon as the database is back.

### Other consumer settings

- `auto-offset-reset: earliest`: a new consumer group starts at the beginning of the topic, so the first start of
  analysis-service replays every run ingested so far (the replay promised in [ADR-0004](0004-kafka-as-event-backbone.md)).
- Three concurrent consumers, one per partition of `testpulse.test-runs`.

### Consequences

- Good: duplicates have no effect, and a failure never loses an event.
- Good: one bad record cannot stop the processing of a repository's later runs.
- Bad: skipped records are only visible in the logs. A dead letter topic (option C), from which they can be inspected
  and replayed, is planned after the MVP.
- Bad: a persistent bug blocks its partition until it is fixed. This is visible (the consumer lag grows, the error is
  logged on every retry) and preferable to silently dropping data.
- Bad: the processed-event table grows with every event; old rows can be deleted once they are older than the longest
  possible redelivery delay.
