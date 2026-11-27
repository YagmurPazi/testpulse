-- Events Kafka can never accept (larger than its message limit) are marked instead of retried forever:
-- the relay publishes in order, so one such event held back every later event (ADR-0013).
ALTER TABLE outbox_event
    ADD COLUMN rejected_at TIMESTAMPTZ,
    ADD COLUMN rejection   TEXT;

DROP INDEX ix_outbox_event_unpublished;
CREATE INDEX ix_outbox_event_pending ON outbox_event (created_at)
    WHERE published_at IS NULL AND rejected_at IS NULL;
