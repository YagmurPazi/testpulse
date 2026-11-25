-- One row per CI run attempt. The unique identity makes ingestion idempotent:
-- INSERT ... ON CONFLICT on this constraint decides which of two concurrent uploads wins.
CREATE TABLE test_run (
    id              UUID         PRIMARY KEY,
    repository      VARCHAR(200) NOT NULL,
    external_run_id VARCHAR(128) NOT NULL,
    attempt         INTEGER      NOT NULL CHECK (attempt >= 1),
    commit_sha      VARCHAR(64)  NOT NULL,
    branch          VARCHAR(255) NOT NULL,
    ingested_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_test_run_identity UNIQUE (repository, external_run_id, attempt)
);

-- Results keep the order of the report (position) so a run reads back exactly as it was stored.
CREATE TABLE test_case_result (
    run_id          UUID        NOT NULL REFERENCES test_run (id) ON DELETE CASCADE,
    position        INTEGER     NOT NULL,
    class_name      TEXT        NOT NULL,
    test_name       TEXT        NOT NULL,
    outcome         VARCHAR(16) NOT NULL CHECK (outcome IN ('PASSED', 'FAILED', 'ERROR', 'SKIPPED')),
    duration_ms     BIGINT      NOT NULL CHECK (duration_ms >= 0),
    rerun_failures  INTEGER     NOT NULL CHECK (rerun_failures >= 0),
    failure_message TEXT,
    PRIMARY KEY (run_id, position)
);

-- Transactional outbox (ADR-0006): events are written in the same transaction as the data they describe,
-- and a relay publishes them to Kafka afterwards.
CREATE TABLE outbox_event (
    id           UUID         PRIMARY KEY,
    topic        VARCHAR(200) NOT NULL,
    message_key  VARCHAR(200) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

-- The relay only ever looks for unpublished events, oldest first.
CREATE INDEX ix_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
