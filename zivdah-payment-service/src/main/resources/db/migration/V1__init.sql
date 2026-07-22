CREATE TABLE IF NOT EXISTS payments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT        NOT NULL,
    user_id         BIGINT,
    amount          DECIMAL(10,2) NOT NULL,
    method          VARCHAR(50),
    status          VARCHAR(50)   NOT NULL DEFAULT 'INITIATED',
    transaction_id  VARCHAR(255),
    created_at      TIMESTAMP     DEFAULT NOW()
);
