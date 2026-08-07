-- Bring the "orders" table in line with fields the Order entity has always expected but V1 never created.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS order_number      VARCHAR(50),
    ADD COLUMN IF NOT EXISTS sub_total          DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS gst_amount         DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS cgst_amount        DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS sgst_amount        DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS igst_amount        DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS total_tax_amount   DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS delivery_charge    DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS packaging_charge   DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS handling_charge    DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS discount_amount    DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS coupon_code        VARCHAR(50),
    ADD COLUMN IF NOT EXISTS currency           VARCHAR(10),
    ADD COLUMN IF NOT EXISTS delivery_country   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_at         TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_order_number ON orders(order_number);

-- Vendor ownership on order line items, so a vendor's orders can be looked up without
-- coupling order-service to product-service. Populated by the ordering client at checkout
-- time (same trust level as the already client-supplied price/subtotal on this table).
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS vendor_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_order_items_vendor ON order_items(vendor_id);
