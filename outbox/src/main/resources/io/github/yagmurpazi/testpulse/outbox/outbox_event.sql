-- Table required by the outbox module. Copy into the service's own Flyway migration;
-- each service owns its schema, so the module does not migrate databases itself.
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
