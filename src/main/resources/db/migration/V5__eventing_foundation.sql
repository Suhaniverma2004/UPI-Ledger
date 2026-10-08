ALTER TABLE outbox
    ADD COLUMN correlation_id UUID,
    ADD COLUMN last_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_error VARCHAR(2000);

