ALTER TABLE vendor_payouts ADD COLUMN IF NOT EXISTS initiated_by_role VARCHAR(20);
ALTER TABLE vendor_payouts ADD COLUMN IF NOT EXISTS initiated_by_user_id BIGINT;

UPDATE vendor_payouts SET initiated_by_role = 'VENDOR', initiated_by_user_id = vendor_id
    WHERE initiated_by_role IS NULL;

ALTER TABLE vendor_payouts ALTER COLUMN initiated_by_role SET NOT NULL;
ALTER TABLE vendor_payouts ALTER COLUMN initiated_by_user_id SET NOT NULL;

ALTER TABLE vendor_payouts ADD CONSTRAINT chk_vendor_payouts_initiated_by_role
    CHECK (initiated_by_role IN ('VENDOR','ADMIN'));
ALTER TABLE vendor_payouts ADD CONSTRAINT chk_vendor_payouts_payout_mode
    CHECK (payout_mode IS NULL OR payout_mode IN ('UPI','IMPS','NEFT','RTGS'));

CREATE INDEX IF NOT EXISTS idx_vendor_payouts_initiated_by_user_id ON vendor_payouts(initiated_by_user_id);

-- At most one open (REQUESTED/PROCESSING) payout per vendor at a time — DB-level so it's
-- race-proof, not just an app-level check. Verified against current dev data: 0 vendors
-- currently have more than one open payout, and the only existing payout_mode value is 'UPI'.
CREATE UNIQUE INDEX IF NOT EXISTS uq_vendor_payouts_open_per_vendor
    ON vendor_payouts (vendor_id)
    WHERE status IN ('REQUESTED', 'PROCESSING');
