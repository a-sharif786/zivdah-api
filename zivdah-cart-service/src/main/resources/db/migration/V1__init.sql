CREATE TABLE IF NOT EXISTS cart_items (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT         NOT NULL,
    product_id  BIGINT         NOT NULL,
    quantity    INTEGER        NOT NULL,
    price       DECIMAL(10,2)  NOT NULL,
    subtotal    DECIMAL(10,2)  NOT NULL,
    sku         VARCHAR(255),
    status      VARCHAR(50)    NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP      NOT NULL DEFAULT NOW(),
    deleted     BOOLEAN        NOT NULL DEFAULT FALSE
);
