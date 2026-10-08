-- MPIN (mobile PIN) quick login — see MpinServiceImpl.
--
-- One row per (user, device), mirroring refresh_tokens: a user can have MPIN on several phones
-- and each must be revocable on its own. A PIN alone (10^6 values) is never enough to log in —
-- the device must also present the random 256-bit secret it was issued at setup, stored here
-- only as its SHA-256 hash (same scheme as refresh_tokens.token_hash). The PIN itself is BCrypt.
--
-- Lockout: each login/change attempt reserves one of failed_attempts BEFORE the PIN is checked
-- (so concurrent guesses can't overshoot), and reaching the max sets revoked_at — the device
-- must do a full login and set up MPIN again. Revoked rows are reused by the next setup on the
-- same device, hence UNIQUE(user_id, device_id) rather than a partial index.
CREATE TABLE IF NOT EXISTS mpin_devices (
    id                  BIGSERIAL     PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id           VARCHAR(64)   NOT NULL,
    device_name         VARCHAR(100),
    device_secret_hash  VARCHAR(64)   NOT NULL,
    mpin_hash           VARCHAR(100)  NOT NULL,
    failed_attempts     INT           NOT NULL DEFAULT 0,
    last_used_at        TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL,
    updated_at          TIMESTAMP     NOT NULL,
    revoked_at          TIMESTAMP,
    CONSTRAINT uq_mpin_devices_user_device UNIQUE (user_id, device_id)
);

-- When the user last FULLY authenticated (password / OTP) for this refresh-token family.
-- Copied onto every rotation and into the access JWT's auth_time claim, so MPIN setup can
-- require a recent full login. NULL for MPIN-started families and pre-V7 rows = "not fresh".
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS auth_time TIMESTAMP;
