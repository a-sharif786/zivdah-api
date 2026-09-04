-- The baseline-on-migrate fix (flyway_schema_history_product) baselined this service at
-- version 1, so V1__init.sql was marked "already applied" and skipped — but the live
-- products table predates that migration and never actually got the `fav` column it defines.
-- Re-add it here since V1 can't be re-run (already checksummed as skipped).
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS fav BOOLEAN NOT NULL DEFAULT FALSE;
