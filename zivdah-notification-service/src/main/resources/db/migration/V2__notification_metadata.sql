-- Adds recipient/notification-type metadata for the actual "notification history" screens,
-- read/unread state, a dedup key so an at-least-once Kafka redelivery doesn't push twice,
-- and retry bookkeeping for failed sends. All nullable/defaulted — backward compatible with
-- existing rows from V1.
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS recipient_role     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS notification_type  VARCHAR(40),
    ADD COLUMN IF NOT EXISTS entity_type         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS entity_id           BIGINT,
    ADD COLUMN IF NOT EXISTS is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS read_at             TIMESTAMP,
    ADD COLUMN IF NOT EXISTS dedup_key           VARCHAR(150),
    ADD COLUMN IF NOT EXISTS retry_count         INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_retry_at       TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_dedup_key ON notifications (dedup_key)
    WHERE dedup_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_retry
    ON notifications (status, retry_count, next_retry_at)
    WHERE status = 'FAILED';
