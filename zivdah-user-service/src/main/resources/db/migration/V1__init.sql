CREATE TABLE IF NOT EXISTS user_addresses (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    address_line1    VARCHAR(255),
    address_line2    VARCHAR(255),
    city             VARCHAR(100),
    state            VARCHAR(100),
    pin_code         VARCHAR(20),
    is_default       BOOLEAN      DEFAULT FALSE,
    created_at       TIMESTAMP    DEFAULT NOW(),
    updated_at       TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_address_user ON user_addresses(user_id);
