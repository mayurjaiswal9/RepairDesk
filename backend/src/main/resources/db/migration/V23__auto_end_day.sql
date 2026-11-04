-- Admin can turn automatic end of day off. Checked (on) by default.
ALTER TABLE business_settings
    ADD COLUMN auto_end_day BOOLEAN NOT NULL DEFAULT TRUE;
