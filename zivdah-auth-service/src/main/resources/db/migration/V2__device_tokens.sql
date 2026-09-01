-- Multi-device FCM token store. Replaces the single user_sessions.device_token column
-- (kept for backward compatibility, no longer used for push) with one row per physical
-- device registration, so a user can be signed in on several devices/browsers at once.
CREATE TABLE IF NOT EXISTS device_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    user_role    VARCHAR(20)  NOT NULL,
    device_type  VARCHAR(10)  NOT NULL, -- ANDROID, IOS, WEB
    fcm_token    VARCHAR(500) NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_tokens_fcm_token UNIQUE (fcm_token)
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user ON device_tokens (user_id, is_active);
