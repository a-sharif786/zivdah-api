-- V1__init.sql never actually ran against the shared zivdahDB: this service's very first
-- Flyway run found the (other services') schema non-empty, so baseline-on-migrate baselined
-- flyway_schema_history_delivery at version 1 and treated V1 as already applied — it was
-- silently SKIPPED, not executed (confirmed live: flyway_schema_history_delivery has only the
-- "<< Flyway Baseline >>" row, and the `deliveries` table does not exist). Every order's
-- delivery therefore never got created, at any status, for any delivery boy. This migration
-- is the actual first schema change that runs, and is fully idempotent so it's also safe on
-- an environment where V1 genuinely did execute (a fresh, empty DB).
CREATE TABLE IF NOT EXISTS deliveries (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT      NOT NULL,
    vendor_id       BIGINT,
    user_id         BIGINT      NOT NULL,
    delivery_boy_id BIGINT,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    failure_reason  VARCHAR(30),
    failure_note    VARCHAR(500),
    assigned_at     TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- vendor_id is nullable: most products in this catalog are platform-owned (no vendor_id —
-- see product-service's V4__vendor_ownership.sql), so most orders' items all have a null
-- vendorId (OrderItemDto.vendorId javadoc: "Null = platform-owned product"). Under the
-- previous NOT NULL vendor_id, OrderServiceClient#getVendorIds filtered those null vendor ids
-- out entirely, so createPendingDeliveriesForOrder had nothing to iterate and created zero
-- Delivery rows for the vast majority of real orders. In case V1 *did* run somewhere with the
-- original NOT NULL column, relax it here too.
ALTER TABLE deliveries ALTER COLUMN vendor_id DROP NOT NULL;

-- V1's plain UNIQUE(order_id, vendor_id) only guarded vendor-owned deliveries: Postgres does
-- not treat repeated NULLs as duplicates under a standard UNIQUE constraint, so it would
-- happily allow more than one no-vendor delivery per order. Split into two partial unique
-- indexes so both the vendor-owned and platform/no-vendor cases are still guarded against
-- duplicate creation (see DeliveryServiceImpl#createPendingDeliveriesForOrder).
ALTER TABLE deliveries DROP CONSTRAINT IF EXISTS uq_deliveries_order_vendor;
CREATE UNIQUE INDEX IF NOT EXISTS uq_deliveries_order_vendor
    ON deliveries (order_id, vendor_id) WHERE vendor_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_deliveries_order_no_vendor
    ON deliveries (order_id) WHERE vendor_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_deliveries_vendor ON deliveries (vendor_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_delivery_boy ON deliveries (delivery_boy_id);
