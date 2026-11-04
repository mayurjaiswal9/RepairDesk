-- The ACE settings rows already carry company_id. This only changes constraints.
-- Existing pin, switches, working hours, and usage counts are not rewritten.

ALTER TABLE business_settings DROP CONSTRAINT business_settings_singleton_check;
ALTER TABLE location_settings DROP CONSTRAINT location_settings_singleton_check;

ALTER TABLE business_settings
    ADD CONSTRAINT ux_business_settings_company UNIQUE (company_id);
ALTER TABLE location_settings
    ADD CONSTRAINT ux_location_settings_company UNIQUE (company_id);

ALTER TABLE working_hours DROP CONSTRAINT ux_working_hours_dow;
ALTER TABLE working_hours
    ADD CONSTRAINT ux_working_hours_company_dow UNIQUE (company_id, day_of_week);

-- Past usage rows stay null. New rows may record a company. The daily key stays (day, api)
-- until a second company is allowed, so existing counts are not split or reset.
ALTER TABLE maps_api_usage ADD COLUMN company_id uuid;
ALTER TABLE maps_api_usage
    ADD CONSTRAINT fk_maps_api_usage_company FOREIGN KEY (company_id) REFERENCES companies (id);
