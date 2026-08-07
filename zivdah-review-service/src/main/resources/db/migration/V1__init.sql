CREATE TABLE IF NOT EXISTS reviews (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    rating     INTEGER       NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment    VARCHAR(500)  NOT NULL,
    created_at TIMESTAMP     DEFAULT NOW()
);
