-- Per-category brand configuration (PROD-004): a device category can either
-- offer a curated, searchable list of brands (CONFIGURED) or fall back to
-- free-text entry (FREE_TEXT, the default for existing categories).
ALTER TABLE device_categories
    ADD COLUMN brand_mode varchar(20) NOT NULL DEFAULT 'FREE_TEXT';

CREATE TABLE device_brands (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_category_id  uuid NOT NULL REFERENCES device_categories(id),
    name                varchar(100) NOT NULL,
    archived            boolean NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz,
    version             bigint NOT NULL DEFAULT 0
);

CREATE INDEX ix_device_brands_category ON device_brands (device_category_id);

-- Unique active brand name per category (case-insensitive), mirroring the
-- device_categories active-name-uniqueness pattern.
CREATE UNIQUE INDEX ux_device_brands_category_name_active
    ON device_brands (device_category_id, lower(name)) WHERE archived = false;
