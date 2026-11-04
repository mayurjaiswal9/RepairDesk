-- V12: customer service address and map pin on requests and jobs.
-- Nullable so existing rows stay valid. A pin is stored only when both coordinates are present.

ALTER TABLE repair_requests
    ADD COLUMN service_address_line varchar(300),
    ADD COLUMN service_landmark     varchar(150),
    ADD COLUMN service_city         varchar(100),
    ADD COLUMN service_pincode      varchar(6),
    ADD COLUMN service_lat          double precision,
    ADD COLUMN service_lng          double precision,
    ADD COLUMN service_pin_source   varchar(20);

ALTER TABLE repair_requests
    ADD CONSTRAINT requests_pin_source_check
        CHECK (service_pin_source IS NULL OR service_pin_source IN ('GEOCODED', 'MANUAL_PIN', 'CHECK_IN')),
    ADD CONSTRAINT requests_pincode_check
        CHECK (service_pincode IS NULL OR service_pincode ~ '^[1-9][0-9]{5}$'),
    ADD CONSTRAINT requests_service_coords_check
        CHECK (
            (service_lat IS NULL AND service_lng IS NULL)
            OR (
                service_lat IS NOT NULL AND service_lng IS NOT NULL
                AND service_lat BETWEEN -90 AND 90
                AND service_lng BETWEEN -180 AND 180
            )
        );

ALTER TABLE jobs
    ADD COLUMN service_address_line varchar(300),
    ADD COLUMN service_landmark     varchar(150),
    ADD COLUMN service_city         varchar(100),
    ADD COLUMN service_pincode      varchar(6),
    ADD COLUMN service_lat          double precision,
    ADD COLUMN service_lng          double precision,
    ADD COLUMN service_pin_source   varchar(20);

ALTER TABLE jobs
    ADD CONSTRAINT jobs_pin_source_check
        CHECK (service_pin_source IS NULL OR service_pin_source IN ('GEOCODED', 'MANUAL_PIN', 'CHECK_IN')),
    ADD CONSTRAINT jobs_pincode_check
        CHECK (service_pincode IS NULL OR service_pincode ~ '^[1-9][0-9]{5}$'),
    ADD CONSTRAINT jobs_service_coords_check
        CHECK (
            (service_lat IS NULL AND service_lng IS NULL)
            OR (
                service_lat IS NOT NULL AND service_lng IS NOT NULL
                AND service_lat BETWEEN -90 AND 90
                AND service_lng BETWEEN -180 AND 180
            )
        );
