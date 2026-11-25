-- One row per status change and channel that delivered it: the idempotency record of the notifications.
-- A redelivered TestStatusChanged event finds its rows and sends nothing again (ADR-0011).
CREATE TABLE delivered_notification (
    event_id     uuid        NOT NULL,
    channel      text        NOT NULL,
    delivered_at timestamptz NOT NULL,
    PRIMARY KEY (event_id, channel)
);
