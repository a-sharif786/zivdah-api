-- Vendor ownership: a product created by a VENDOR-role user is tagged with their userId
-- (from auth-service, referenced by convention only — no FK, same pattern as orders.user_id
-- elsewhere in this codebase). NULL means platform-owned (created by an ADMIN).
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS vendor_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_products_vendor ON products(vendor_id);
