CREATE TABLE IF NOT EXISTS users (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password       VARCHAR(255) NOT NULL,
    mobile         VARCHAR(20)  NOT NULL UNIQUE,
    role           VARCHAR(50)  NOT NULL DEFAULT 'USER',
    active         BOOLEAN      NOT NULL DEFAULT FALSE,
    mobile_otp     VARCHAR(10),
    email_otp      VARCHAR(10),
    otp_generated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_sessions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL UNIQUE,
    token        VARCHAR(500) NOT NULL,
    device_token VARCHAR(255),
    created_at   TIMESTAMP
);
