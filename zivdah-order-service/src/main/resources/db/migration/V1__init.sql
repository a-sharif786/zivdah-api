CREATE TABLE IF NOT EXISTS orders (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT        NOT NULL,
    total_amount            DECIMAL(10,2) NOT NULL,
    status                  VARCHAR(50)   NOT NULL DEFAULT 'CREATED',
    created_at              TIMESTAMP     DEFAULT NOW(),
    delivery_address_line1  VARCHAR(255),
    delivery_address_line2  VARCHAR(255),
    delivery_city           VARCHAR(100),
    delivery_state          VARCHAR(100),
    delivery_pin_code       VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS order_items (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  BIGINT        NOT NULL,
    quantity    INTEGER       NOT NULL,
    price       DECIMAL(10,2) NOT NULL,
    subtotal    DECIMAL(10,2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);
