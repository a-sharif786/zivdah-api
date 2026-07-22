CREATE TABLE IF NOT EXISTS coupons (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(50)    NOT NULL UNIQUE,
    description         TEXT,
    discount_type       VARCHAR(50)    NOT NULL,
    discount_value      DECIMAL(10,2)  NOT NULL,
    min_order_amount    DECIMAL(10,2),
    max_discount_amount DECIMAL(10,2),
    usage_limit         INTEGER        NOT NULL DEFAULT 1,
    used_count          INTEGER        NOT NULL DEFAULT 0,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    valid_from          TIMESTAMP      NOT NULL,
    valid_until         TIMESTAMP      NOT NULL,
    created_at          TIMESTAMP      DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_coupon_code ON coupons(code);
