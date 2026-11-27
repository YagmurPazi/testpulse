# 0004. Kafka as the event backbone

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

Three services cooperate in one flow: ingestion stores a test run, analysis updates test history and test states,
notification informs people when a state changes. How should they communicate?

Requirements:

- Ingesting a report must not depend on analysis or notification being available or fast.
- New consumers (for example a future duration-anomaly analysis) should be added without changing the producer.
- When a new flakiness strategy is introduced, existing test runs should be **re-processed** to evaluate it on history.
- Results for the same test must be processed in order; a pass followed by a fail is different from the reverse.

## Considered options

1. Synchronous REST calls between services
2. A message broker with queues (RabbitMQ)
3. A distributed log (Apache Kafka)
4. The database as a queue (polling a table)

## Decision outcome

Chosen option: **3, Apache Kafka**, running in KRaft mode (no ZooKeeper).

Services publish events (`TestRunIngested`, `TestStatusChanged`) to topics and consume them in their own consumer
groups.

How each option fares against the requirements:

- **REST** couples availability: if analysis is down, ingestion fails or must build its own retry mechanism.
  The producer must also know every consumer.
- **RabbitMQ** decouples services well and is a strong choice for task queues, but a message is removed once it is
  acknowledged. Re-processing history would require storing the events somewhere else.
- **Kafka** keeps events in an append-only log for a configured retention period. Consumers track their own position
  (offset), so a consumer can be reset and replay history, and a new consumer group can read from the beginning
  without affecting others. Events with the same key go to the same partition, which preserves order per key
  (the key will be chosen in Phase 1, e.g. per repository or per test).
- **Database polling** avoids new infrastructure but adds latency and load, and rebuilds broker features by hand.

### Consequences

- Good: producers and consumers are decoupled in time and availability; ingestion keeps working when analysis is down.
- Good: replay makes it possible to evaluate a new strategy on past data.
- Good: per-key ordering matches the per-test nature of flakiness detection.
- Bad: delivery is **at least once**. A consumer may see the same event twice after a failure or rebalance, so
  every consumer must be idempotent (planned in Phase 1 and 2).
- Bad: the system is **eventually consistent**; the dashboard can briefly lag behind an upload.
- Bad: one more piece of infrastructure to run and understand. Locally this is a single broker with replication
  factor 1; production would need at least three brokers.
- Bad: publishing an event and writing to the database are two separate operations. Keeping them consistent
  (for example with the transactional outbox pattern) is a known problem that must be addressed in Phase 1.
