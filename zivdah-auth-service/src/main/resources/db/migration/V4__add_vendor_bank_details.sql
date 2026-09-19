ALTER TABLE users
    ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS bank_ifsc_code       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS upi_vpa              VARCHAR(100);
