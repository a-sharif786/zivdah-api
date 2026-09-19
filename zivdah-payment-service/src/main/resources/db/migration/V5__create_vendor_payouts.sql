CREATE TABLE IF NOT EXISTS vendor_payouts (
    id                   BIGSERIAL PRIMARY KEY,
    vendor_id            BIGINT         NOT NULL,
    amount               NUMERIC(12,2)  NOT NULL,
    payout_mode          VARCHAR(20),
    account_no           VARCHAR(50),
    ifsc_bank_code       VARCHAR(20),
    payee_vpa            VARCHAR(100),
    status               VARCHAR(20)    NOT NULL DEFAULT 'REQUESTED',
    invoice_number       VARCHAR(100)   NOT NULL UNIQUE,
    gateway_reference_id VARCHAR(100),
    gateway_status       VARCHAR(50),
    gateway_description  VARCHAR(255),
    utr_number           VARCHAR(50),
    rejection_reason     VARCHAR(255),
    requested_at         TIMESTAMP      NOT NULL,
    processed_at         TIMESTAMP,
    settled_at           TIMESTAMP,
    updated_at           TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vendor_payouts_vendor_id ON vendor_payouts(vendor_id);
CREATE INDEX IF NOT EXISTS idx_vendor_payouts_status ON vendor_payouts(status);
