-- Runs automatically the FIRST time the postgres-data volume is initialized
-- (i.e. the data directory is empty). If the zivdah-postgres-data volume
-- already exists, this will NOT run on a plain `docker compose up` — remove
-- the volume first (`docker volume rm zivdah-postgres-data`) to force it.
--
-- Enables extensions commonly needed by the services sharing zivdah_db:
--   pgcrypto  -> gen_random_uuid(), digest(), crypt() for hashing/UUIDs
--   uuid-ossp -> uuid_generate_v4() as an alternative UUID generator
--
-- Safe to re-run manually (psql -f) since both are idempotent.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
