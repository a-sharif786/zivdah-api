-- Released mobile app versions, managed separately per platform (see AppVersionService).
-- `version` is the normalized MAJOR.MINOR.PATCH string shown to clients; the three numeric
-- columns hold the same value split out so "latest" and "is there a newer forced release"
-- are plain ORDER BY / row comparisons in SQL instead of string sorting ("1.10.0" < "1.9.0").
CREATE TABLE IF NOT EXISTS app_versions (
    id             BIGSERIAL     PRIMARY KEY,
    platform       VARCHAR(10)   NOT NULL CHECK (platform IN ('ANDROID', 'IOS')),
    version        VARCHAR(32)   NOT NULL,
    version_major  INT           NOT NULL,
    version_minor  INT           NOT NULL,
    version_patch  INT           NOT NULL,
    store_url      VARCHAR(512)  NOT NULL,
    force_update   BOOLEAN       NOT NULL DEFAULT FALSE,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    release_notes  VARCHAR(2000),
    released_at    TIMESTAMP     NOT NULL,
    created_at     TIMESTAMP     NOT NULL,
    updated_at     TIMESTAMP     NOT NULL,
    CONSTRAINT uq_app_versions_platform_version UNIQUE (platform, version)
);

CREATE INDEX IF NOT EXISTS idx_app_versions_platform_active
    ON app_versions(platform, active, version_major DESC, version_minor DESC, version_patch DESC);
