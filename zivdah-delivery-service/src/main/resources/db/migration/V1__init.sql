-- One row per (orderId, vendorId): an order can span multiple vendors, and each vendor's
-- portion is packed/picked-up/delivered independently.
CREATE TABLE IF NOT EXISTS deliveries (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT      NOT NULL,
    vendor_id       BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    delivery_boy_id BIGINT,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    failure_reason  VARCHAR(30),
    failure_note    VARCHAR(500),
    assigned_at     TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_deliveries_order_vendor UNIQUE (order_id, vendor_id)
);

CREATE INDEX IF NOT EXISTS idx_deliveries_vendor ON deliveries (vendor_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_delivery_boy ON deliveries (delivery_boy_id);
