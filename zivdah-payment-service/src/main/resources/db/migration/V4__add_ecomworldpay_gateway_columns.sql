-- EcomWorldPay UPI QR (PayIn) integration — see com.zivdah.payment.gateway.ecomworldpay.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS gateway_txn_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS upi_intent     TEXT,
    ADD COLUMN IF NOT EXISTS payer_vpa      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS rrn            VARCHAR(100),
    ADD COLUMN IF NOT EXISTS npci_txn_id    VARCHAR(100);
