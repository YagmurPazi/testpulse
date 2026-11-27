# 0013. Large runs and events Kafka cannot accept

- Status: Accepted
- Date: 2026-11-27

## Context and problem statement

ingestion-service published every run as one `TestRunIngested` event carrying all its results
([ADR-0005](0005-event-contracts-and-wire-format.md)). Trying TestPulse on a real open source project,
apache/cassandra-java-driver, showed two problems:

- One configuration of its CI (5,912 tests) makes an event of 1.6 MB. Kafka's producer refuses messages above 1 MB
  (`max.request.size`). The relay retried the refused event forever, and since it publishes in order
  ([ADR-0006](0006-transactional-outbox.md)), every later event of every repository waited behind it.
- 51 of its 528 report files contain a `<testcase>` with an empty name, which Surefire writes when a whole class is
  skipped or fails before its tests run. The parser rejected these files, and with them the whole run.

## Considered options

For large runs:

1. Raise the message limits (producer, broker, topic, consumer) to, say, 20 MB
2. Store the results elsewhere and publish only a reference (claim check)
3. Split a large run into several events

For events Kafka refuses anyway:

- A. Retry forever (as before)
- B. Mark them rejected and continue with the next event

## Decision outcome

Chosen options: **3, split large runs**, and **B, mark refused events rejected.**

### Splitting

- A run is published as parts of at most 512 KB of results, half of Kafka's limit. The size of each result is
  estimated generously; a test checks the estimate against the real JSON.
- The contract gains two optional fields, `part` and `parts`. Under the evolution rules of ADR-0005 additive fields
  keep `schemaVersion` 1. Events written before read as part 1 of 1; they are boxed in Java, because a missing
  primitive fails deserialization with Jackson 3 (the contract tests caught this).
- All results of one test go into the same part, because analysis-service records one execution per test and run.
- Every part has its own event id and is processed on its own. analysis-service needed no change: it evaluates each
  test independently, so it does not matter which part a test arrives in.

Why not 1: it moves the ceiling instead of removing it, needs the same limit in four places, and large messages
slow down every consumer of the partition. Why not 2: consumers would have to call ingestion-service back, which
event-carried state transfer avoids (ADR-0005).

### Rejected events

The relay classifies send failures. A `RecordTooLargeException` (also when Spring Kafka wraps it) can never succeed:
the event is marked with `rejected_at` and the reason, logged as an error, and the events behind it are published.
Every other failure (broker unreachable, timeout) is retried as before. With splitting, a rejection needs a single
test result above 512 KB; this is the safety net, not the normal path.

### Class-level test cases

A `<testcase>` with an empty or missing name is kept as a result of the whole class, named `(class)`, instead of
rejecting the report.

### Consequences

- Good: runs of any size flow through, and no single event can stop the pipeline again.
- Good: the decision is backed by real data: all 528 reports of the cassandra-java-driver run are read, and a run of
  its size is published in parts Kafka accepts (integration test).
- Bad: a rejected event is lost to consumers. It stays in the outbox table with its reason and is logged; a dead
  letter topic and an alert are planned after the MVP.
- Bad: a run's parts can be processed at slightly different times, so for a moment a run can be partly analysed.
