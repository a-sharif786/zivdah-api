-- The `users.role` check constraint (chk_users_role) only allows USER/ADMIN/VENDOR — it
-- predates the DELIVERY_BOY value added to com.zivdah.auth.enums.Role, and was never created
-- by this service's own Flyway migrations (see V1__init.sql: plain VARCHAR, no CHECK) — it
-- came from the manually-maintained consolidated schema dump (zivdah_api_postgresql.sql) that
-- originally seeded the shared zivdahDB, written before that enum value existed. As a result
-- no user can ever actually hold role=DELIVERY_BOY: both self-registration with that role and
-- ADMIN's PUT /update-role/{userId} fail outright with a DB constraint violation, so the
-- entire delivery-boy feature (zivdah-delivery-service's DELIVERY_BOY-scoped endpoints,
-- zivdah-admin's assign-delivery-boy dropdown) has never had a real account to actually use it.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('USER', 'ADMIN', 'VENDOR', 'DELIVERY_BOY'));
