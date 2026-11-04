-- V13: shop pin, location feature settings, technician vehicle type.

ALTER TABLE business_settings
    ADD COLUMN shop_lat double precision,
    ADD COLUMN shop_lng double precision,
    ADD COLUMN geofence_radius_m integer NOT NULL DEFAULT 150;

ALTER TABLE business_settings
    ADD CONSTRAINT business_settings_shop_coords_check
        CHECK (
            (shop_lat IS NULL AND shop_lng IS NULL)
            OR (
                shop_lat IS NOT NULL AND shop_lng IS NOT NULL
                AND shop_lat BETWEEN -90 AND 90
                AND shop_lng BETWEEN -180 AND 180
            )
        ),
    ADD CONSTRAINT business_settings_radius_check
        CHECK (geofence_radius_m BETWEEN 50 AND 1000);

CREATE TABLE location_settings (
    id                              smallint PRIMARY KEY DEFAULT 1,
    check_in_enabled                boolean      NOT NULL DEFAULT false,
    attendance_enabled              boolean      NOT NULL DEFAULT false,
    tracking_enabled                boolean      NOT NULL DEFAULT false,
    travel_enabled                  boolean      NOT NULL DEFAULT false,
    eta_enabled                     boolean      NOT NULL DEFAULT false,
    ping_interval_minutes           integer      NOT NULL DEFAULT 5,
    ping_distance_m                 integer      NOT NULL DEFAULT 200,
    day_auto_end_time               time         NOT NULL DEFAULT '19:00',
    allow_start_outside_geofence    boolean      NOT NULL DEFAULT false,
    far_from_address_m              integer      NOT NULL DEFAULT 300,
    rate_two_wheeler_paise          integer      NOT NULL DEFAULT 500,
    rate_four_wheeler_paise         integer      NOT NULL DEFAULT 1000,
    retention_days                  integer      NOT NULL DEFAULT 90,
    privacy_notice_version          varchar(20)  NOT NULL DEFAULT '1',
    updated_at                      timestamptz,
    version                         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT location_settings_singleton_check CHECK (id = 1),
    CONSTRAINT location_settings_interval_check  CHECK (ping_interval_minutes BETWEEN 1 AND 30),
    CONSTRAINT location_settings_distance_check  CHECK (ping_distance_m BETWEEN 50 AND 5000),
    CONSTRAINT location_settings_far_check       CHECK (far_from_address_m BETWEEN 50 AND 5000),
    CONSTRAINT location_settings_rate_2w_check   CHECK (rate_two_wheeler_paise BETWEEN 0 AND 100000),
    CONSTRAINT location_settings_rate_4w_check   CHECK (rate_four_wheeler_paise BETWEEN 0 AND 100000),
    CONSTRAINT location_settings_retention_check CHECK (retention_days BETWEEN 7 AND 365)
);

INSERT INTO location_settings (id) VALUES (1);

ALTER TABLE users
    ADD COLUMN vehicle_type varchar(20);

ALTER TABLE users
    ADD CONSTRAINT users_vehicle_type_check
        CHECK (vehicle_type IS NULL OR vehicle_type IN ('TWO_WHEELER', 'FOUR_WHEELER'));
