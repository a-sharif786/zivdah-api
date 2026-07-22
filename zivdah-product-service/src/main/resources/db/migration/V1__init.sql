CREATE TABLE IF NOT EXISTS products (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100)   NOT NULL,
    category         VARCHAR(50)    NOT NULL,
    price            DECIMAL(10,2)  NOT NULL,
    discount_price   DECIMAL(10,2),
    unit             VARCHAR(20)    NOT NULL,
    stock_quantity   INTEGER        NOT NULL,
    expiry_date      DATE,
    description      VARCHAR(300),
    image_url        VARCHAR(255),
    organic          BOOLEAN,
    brand            VARCHAR(100),
    created_at       TIMESTAMP      DEFAULT NOW(),
    updated_at       TIMESTAMP      DEFAULT NOW(),
    fav              BOOLEAN        NOT NULL DEFAULT FALSE,
    in_stock         BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_product_name     ON products(name);
CREATE INDEX IF NOT EXISTS idx_product_category ON products(category);

CREATE TABLE IF NOT EXISTS banners (
    id         BIGSERIAL PRIMARY KEY,
    image_url  VARCHAR(255) NOT NULL,
    title      VARCHAR(255),
    active     BOOLEAN DEFAULT TRUE
);
