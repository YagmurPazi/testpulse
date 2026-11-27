-- Table required by the outbox module. Copy into the service's own Flyway migration;
-- each service owns its schema, so the module does not migrate databases itself.
CREATE TABLE outbox_event (
    id           UUID         PRIMARY KEY,
    topic        VARCHAR(200) NOT NULL,
    message_key  VARCHAR(200) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    -- set when Kafka can never accept the event (larger than its message limit); the relay skips it
    rejected_at  TIMESTAMPTZ,
    rejection    TEXT
);

-- The relay only ever looks for pending events, oldest first.
CREATE INDEX ix_outbox_event_pending ON outbox_event (created_at)
    WHERE published_at IS NULL AND rejected_at IS NULL;
