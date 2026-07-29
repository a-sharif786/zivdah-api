CREATE TABLE IF NOT EXISTS wishlists (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    product_id  BIGINT       NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at  TIMESTAMP    DEFAULT NOW(),
    CONSTRAINT uq_wishlist_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_wishlist_user ON wishlists(user_id);

-- fav was a single boolean column on products, shared by every user.
-- Wishlist membership is now tracked per-user in the wishlists table above.
ALTER TABLE products DROP COLUMN IF EXISTS fav;
