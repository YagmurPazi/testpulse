-- Tests TestPulse watches, identified by repository, class and name.
CREATE TABLE monitored_test (
    id              UUID         PRIMARY KEY,
    repository      VARCHAR(200) NOT NULL,
    class_name      TEXT         NOT NULL,
    test_name       TEXT         NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('STABLE', 'SUSPICIOUS', 'FLAKY', 'QUARANTINED')),
    first_seen_at   TIMESTAMPTZ  NOT NULL,
    last_changed_at TIMESTAMPTZ,
    CONSTRAINT uq_monitored_test_key UNIQUE (repository, class_name, test_name)
);

-- Dashboard: tests of a repository by status.
CREATE INDEX ix_monitored_test_repository_status ON monitored_test (repository, status);

-- Append-only history: one row per test and run.
CREATE TABLE test_execution (
    test_id        UUID        NOT NULL REFERENCES monitored_test (id),
    run_id         UUID        NOT NULL,
    commit_sha     VARCHAR(64) NOT NULL,
    attempt        INTEGER     NOT NULL CHECK (attempt >= 1),
    outcome        VARCHAR(16) NOT NULL CHECK (outcome IN ('PASSED', 'FAILED', 'ERROR', 'SKIPPED')),
    rerun_failures INTEGER     NOT NULL CHECK (rerun_failures >= 0),
    duration_ms    BIGINT      NOT NULL CHECK (duration_ms >= 0),
    executed_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (test_id, run_id)
);

-- Latest executions of a test: an index scan that stops after the window (see findLatest).
CREATE INDEX ix_test_execution_latest ON test_execution (test_id, executed_at DESC);

-- Every status change, for the test history on the dashboard.
CREATE TABLE status_change (
    id                UUID        PRIMARY KEY,
    test_id           UUID        NOT NULL REFERENCES monitored_test (id),
    from_status       VARCHAR(16) NOT NULL,
    to_status         VARCHAR(16) NOT NULL,
    reason            TEXT        NOT NULL,
    changed_at        TIMESTAMPTZ NOT NULL,
    triggering_run_id UUID
);

CREATE INDEX ix_status_change_test ON status_change (test_id, changed_at);

-- Idempotent consumer: an event id is stored in the same transaction as its effects (ADR-0008).
CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Transactional outbox, as defined by the outbox module (ADR-0006).
CREATE TABLE outbox_event (
    id           UUID         PRIMARY KEY,
    topic        VARCHAR(200) NOT NULL,
    message_key  VARCHAR(200) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX ix_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
