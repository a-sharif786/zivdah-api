ALTER TABLE products
    ADD COLUMN IF NOT EXISTS image_public_id     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS image_resource_type  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS image_format         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS image_size_bytes      BIGINT;

ALTER TABLE banners
    ADD COLUMN IF NOT EXISTS image_public_id     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS image_resource_type  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS image_format         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS image_size_bytes      BIGINT;
