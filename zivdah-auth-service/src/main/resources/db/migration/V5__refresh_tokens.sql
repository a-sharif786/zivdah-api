-- Opaque, rotating refresh tokens (see RefreshTokenService). Only the SHA-256 hash of each
-- token is stored, never the raw value. A separate table rather than a column on
-- user_sessions: that table is one-row-per-user (user_id UNIQUE), but a user can be signed
-- in on several devices at once, each with its own refresh-token chain (family_id).
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    family_id   VARCHAR(36) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    revoked_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id   ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id ON refresh_tokens(family_id);
