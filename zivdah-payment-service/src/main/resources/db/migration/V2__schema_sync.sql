-- Bring the "payments" table in line with fields the Payment entity has always expected but V1 never created.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS currency              VARCHAR(10),
    ADD COLUMN IF NOT EXISTS gateway_response       TEXT,
    ADD COLUMN IF NOT EXISTS paid_at                TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at             TIMESTAMP,
    ADD COLUMN IF NOT EXISTS gateway_name           VARCHAR(100),
    ADD COLUMN IF NOT EXISTS payment_reference      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS failure_reason         VARCHAR(500),
    ADD COLUMN IF NOT EXISTS refund_transaction_id  VARCHAR(255);
