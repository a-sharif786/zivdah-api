-- Client-generated key identifying one checkout attempt end-to-end, so retrying "Place Order"
-- for the same cart is idempotent instead of inserting a second order row.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_idempotency_key ON orders (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
