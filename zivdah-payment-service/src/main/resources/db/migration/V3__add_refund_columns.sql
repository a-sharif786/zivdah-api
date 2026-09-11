ALTER TABLE payments ADD COLUMN refund_amount DECIMAL(10,2);
ALTER TABLE payments ADD COLUMN refunded_at TIMESTAMP;
