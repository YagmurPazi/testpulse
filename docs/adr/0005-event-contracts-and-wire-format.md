# 0005. Event contracts and wire format

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

Services exchange events over Kafka ([ADR-0004](0004-kafka-as-event-backbone.md)). Producer and consumers must agree
on the shape of each event, on how it is serialized, and on how the shape may change over time. Three questions:

1. What does an event carry: only a reference, or the full data?
2. How is it serialized, and where is the schema defined?
3. How do we change it without breaking consumers?

## Considered options

Event content:

- A. Thin event: `TestRunIngested(runId)`; consumers call ingestion-service's API for the details
- B. One fat event per run carrying all test results (event-carried state transfer)
- C. One event per test result

Serialization and schema:

- D. JSON, schema defined as Java records in a shared `event-contracts` module
- E. Avro with a Schema Registry
- F. JSON, each service defines its own copy of the event classes

## Decision outcome

Chosen options: **B, one fat event per run**, serialized as **D, JSON with Java records in `event-contracts`**.

- `TestRunIngested` carries the run metadata and every `TestCaseResult`.
- Record key: the repository (`owner/name`). Runs of one repository land in the same partition and are consumed in
  order; different repositories are processed in parallel.
- Every event has an `eventId` (consumers deduplicate on it) and a `schemaVersion`.
- The published format is pinned by a golden JSON file and contract tests in `event-contracts`.

Why B over A: with a thin event, analysis-service would depend on ingestion-service being up, and replaying the topic
would not be enough to rebuild state. Why B over C: per-result events multiply the message count by the number of tests
and lose the notion of a run, which the same-commit flakiness strategy needs.

Why D over E: Avro and a Schema Registry enforce compatibility automatically, but add a container, a build plugin and a
new technology to learn, for three services in one repository. The contract tests give the guarantee we need now.
Why D over F: separate copies drift silently; a shared module turns a breaking change into a compile or test failure.

### Evolution rules

- Adding an optional field keeps the version. Consumers ignore unknown fields (tested), producers may add them.
- Renaming or removing a field, or changing its meaning or type, is a breaking change: it needs a new `schemaVersion`
  and a migration period in which consumers understand both versions.
- The golden file for a published version is never edited.

### Consequences

- Good: consumers are independent of the producer's availability; replaying the topic rebuilds their state.
- Good: a breaking change to an event fails the build of the contracts module before it reaches a consumer.
- Bad: **coupling through the shared module.** All services compile against the same classes, so a contract change
  forces a rebuild of every service. In a monorepo with one build this is cheap; with independently deployed services
  it would argue for a schema registry instead.
- Bad: **message size.** Kafka's default limit is about 1 MB per message. A run with thousands of tests and long failure
  messages could exceed it. Mitigations: failure messages are truncated to 2,000 characters, and the producer compresses
  batches (zstd). If real data still exceeds the limit, a run will be split into chunks in a later version.
- Bad: ordering is per repository, not per test, so one very busy repository cannot be parallelized across partitions.
  This is acceptable at TestPulse's scale.
