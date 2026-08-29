CREATE TABLE IF NOT EXISTS logs (
    id              BIGSERIAL PRIMARY KEY,
    service_name    VARCHAR(100) NOT NULL,
    log_level       VARCHAR(10)  NOT NULL,
    logger_name     VARCHAR(255),
    message         TEXT,
    exception       TEXT,
    correlation_id  VARCHAR(64),
    thread_name     VARCHAR(100),
    logged_at       TIMESTAMPTZ  NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_logs_service_name   ON logs(service_name);
CREATE INDEX IF NOT EXISTS idx_logs_log_level      ON logs(log_level);
CREATE INDEX IF NOT EXISTS idx_logs_logged_at      ON logs(logged_at DESC);
CREATE INDEX IF NOT EXISTS idx_logs_correlation_id ON logs(correlation_id);
