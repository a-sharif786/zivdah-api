-- Invoice Management: one invoice per successfully-paid order (see InvoiceService).
-- order_id has a UNIQUE FK to this service's own orders table — that uniqueness is the
-- hard, DB-level guarantee behind idempotent invoice generation, not just an app-level check.

CREATE TABLE IF NOT EXISTS invoice_number_counters (
    year     INT PRIMARY KEY,
    last_seq BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS invoices (
    id               BIGSERIAL PRIMARY KEY,
    invoice_number   VARCHAR(32)   NOT NULL UNIQUE,
    order_id         BIGINT        NOT NULL UNIQUE REFERENCES orders(id),
    customer_id      BIGINT        NOT NULL,
    customer_name    VARCHAR(255),
    customer_email   VARCHAR(255),

    subtotal         NUMERIC(12,2) NOT NULL,
    discount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    delivery_fee     NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax              NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount     NUMERIC(12,2) NOT NULL,

    payment_status   VARCHAR(32)   NOT NULL,
    payment_method   VARCHAR(32),
    transaction_id   VARCHAR(100),

    invoice_date     TIMESTAMP     NOT NULL,
    pdf_url          VARCHAR(500)  NOT NULL,

    created_at       TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_invoices_customer_id ON invoices(customer_id);
