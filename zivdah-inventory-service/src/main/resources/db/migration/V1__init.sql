CREATE TABLE IF NOT EXISTS inventory (
    id                  BIGSERIAL PRIMARY KEY,
    product_id          BIGINT    NOT NULL UNIQUE,
    available_quantity  INTEGER   NOT NULL DEFAULT 0,
    reserved_quantity   INTEGER   NOT NULL DEFAULT 0,
    last_updated        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inventory_reservations (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT      NOT NULL,
    product_id  BIGINT      NOT NULL,
    quantity    INTEGER     NOT NULL,
    status      VARCHAR(50) NOT NULL DEFAULT 'RESERVED'
);

CREATE INDEX IF NOT EXISTS idx_reservation_order ON inventory_reservations(order_id);
