-- V17: a pin taken from a link the customer sent, and counters for place suggestions.
-- Existing rows are unchanged. GEOCODED, MANUAL_PIN, and CHECK_IN stay valid.

ALTER TABLE repair_requests DROP CONSTRAINT requests_pin_source_check;
ALTER TABLE repair_requests ADD CONSTRAINT requests_pin_source_check
    CHECK (service_pin_source IS NULL OR service_pin_source IN ('GEOCODED', 'MANUAL_PIN', 'CHECK_IN', 'CUSTOMER_LINK'));

ALTER TABLE jobs DROP CONSTRAINT jobs_pin_source_check;
ALTER TABLE jobs ADD CONSTRAINT jobs_pin_source_check
    CHECK (service_pin_source IS NULL OR service_pin_source IN ('GEOCODED', 'MANUAL_PIN', 'CHECK_IN', 'CUSTOMER_LINK'));

ALTER TABLE maps_api_usage DROP CONSTRAINT maps_api_usage_api_check;
ALTER TABLE maps_api_usage ADD CONSTRAINT maps_api_usage_api_check
    CHECK (api IN ('GEOCODE', 'ROUTES', 'SUGGESTIONS', 'PLACE_DETAILS'));
