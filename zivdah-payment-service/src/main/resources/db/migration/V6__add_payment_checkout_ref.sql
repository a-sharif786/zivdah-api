-- A payment intent can now be validated/created before the order it will belong to exists
-- (see PaymentServiceImpl#initiatePayment) — order_id is attached later via linkOrder().
ALTER TABLE payments ALTER COLUMN order_id DROP NOT NULL;

-- Client-generated key identifying one checkout attempt end-to-end, so retrying "Place Order"
-- for the same cart is idempotent instead of inserting a second payment row.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS checkout_ref VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_checkout_ref ON payments (checkout_ref)
    WHERE checkout_ref IS NOT NULL;
