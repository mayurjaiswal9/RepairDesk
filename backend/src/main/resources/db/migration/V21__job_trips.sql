-- Per-job trips recorded by the technician app, their GPS points, and jobs
-- started without a trip. Trip totals stay with the job; points are deleted
-- after location_settings.retention_days.

CREATE TABLE job_trips (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID NOT NULL REFERENCES companies (id),
    job_id              UUID NOT NULL REFERENCES jobs (id),
    technician_id       UUID NOT NULL REFERENCES users (id),
    status              VARCHAR(20) NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL,
    start_lat           DOUBLE PRECISION,
    start_lng           DOUBLE PRECISION,
    start_accuracy_m    DOUBLE PRECISION,
    start_mocked        BOOLEAN NOT NULL DEFAULT FALSE,
    start_kind          VARCHAR(20) NOT NULL,
    start_job_id        UUID REFERENCES jobs (id),
    ended_at            TIMESTAMPTZ,
    end_lat             DOUBLE PRECISION,
    end_lng             DOUBLE PRECISION,
    end_accuracy_m      DOUBLE PRECISION,
    end_reason          VARCHAR(20),
    tracked_distance_m  INT NOT NULL DEFAULT 0,
    estimated_gap_m     INT NOT NULL DEFAULT 0,
    planned_distance_m  INT,
    planned_duration_s  INT,
    planned_estimated   BOOLEAN NOT NULL DEFAULT FALSE,
    point_count         INT NOT NULL DEFAULT 0,
    gap_count           INT NOT NULL DEFAULT 0,
    mocked_points       INT NOT NULL DEFAULT 0,
    flags               VARCHAR(200),
    vehicle_type        VARCHAR(20),
    rate_paise          INT,
    adjusted_distance_m INT,
    adjust_reason       VARCHAR(500),
    adjusted_by         UUID REFERENCES users (id),
    adjusted_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT job_trips_status_check CHECK (status IN ('ACTIVE', 'ENDED', 'CANCELLED')),
    CONSTRAINT job_trips_start_kind_check CHECK (
        start_kind IN ('SHOP', 'PREVIOUS_JOB', 'OTHER', 'UNKNOWN')),
    CONSTRAINT job_trips_end_reason_check CHECK (
        end_reason IS NULL
        OR end_reason IN ('ARRIVED', 'GEOFENCE', 'JOB_STARTED', 'REPLACED', 'TIMEOUT', 'CANCELLED')),
    CONSTRAINT job_trips_vehicle_type_check CHECK (
        vehicle_type IS NULL OR vehicle_type IN ('TWO_WHEELER', 'FOUR_WHEELER')),
    CONSTRAINT job_trips_ended_check CHECK ((status = 'ACTIVE') = (ended_at IS NULL)),
    CONSTRAINT job_trips_start_pair CHECK ((start_lat IS NULL) = (start_lng IS NULL)),
    CONSTRAINT job_trips_end_pair CHECK ((end_lat IS NULL) = (end_lng IS NULL)),
    CONSTRAINT job_trips_start_lat_range CHECK (start_lat IS NULL OR start_lat BETWEEN -90 AND 90),
    CONSTRAINT job_trips_start_lng_range CHECK (start_lng IS NULL OR start_lng BETWEEN -180 AND 180),
    CONSTRAINT job_trips_end_lat_range CHECK (end_lat IS NULL OR end_lat BETWEEN -90 AND 90),
    CONSTRAINT job_trips_end_lng_range CHECK (end_lng IS NULL OR end_lng BETWEEN -180 AND 180),
    CONSTRAINT job_trips_distance_check CHECK (tracked_distance_m >= 0 AND estimated_gap_m >= 0),
    CONSTRAINT job_trips_adjusted_check CHECK (
        adjusted_distance_m IS NULL OR adjusted_distance_m BETWEEN 0 AND 1000000)
);

CREATE INDEX ix_job_trips_company_technician_started ON job_trips (company_id, technician_id, started_at);
CREATE INDEX ix_job_trips_job ON job_trips (job_id);
CREATE UNIQUE INDEX ux_job_trips_one_active ON job_trips (technician_id) WHERE status = 'ACTIVE';

CREATE TABLE trip_points (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    trip_id         UUID NOT NULL REFERENCES job_trips (id) ON DELETE CASCADE,
    client_point_id VARCHAR(64) NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL,
    lat             DOUBLE PRECISION NOT NULL,
    lng             DOUBLE PRECISION NOT NULL,
    accuracy_m      DOUBLE PRECISION,
    speed_mps       DOUBLE PRECISION,
    mocked          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_trip_points_client_point UNIQUE (trip_id, client_point_id),
    CONSTRAINT trip_points_lat_range CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT trip_points_lng_range CHECK (lng BETWEEN -180 AND 180)
);

CREATE INDEX ix_trip_points_trip_recorded ON trip_points (trip_id, recorded_at);

CREATE TABLE job_no_trip_reasons (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id    UUID NOT NULL REFERENCES companies (id),
    job_id        UUID NOT NULL REFERENCES jobs (id),
    technician_id UUID NOT NULL REFERENCES users (id),
    reason_code   VARCHAR(30) NOT NULL,
    reason_text   VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT job_no_trip_reasons_code_check CHECK (
        reason_code IN ('AT_SHOP', 'ALREADY_ON_SITE', 'PHONE_PROBLEM', 'OTHER')),
    CONSTRAINT job_no_trip_reasons_other_text_check CHECK (
        reason_code <> 'OTHER' OR reason_text IS NOT NULL)
);

CREATE INDEX ix_job_no_trip_reasons_company_technician_created
    ON job_no_trip_reasons (company_id, technician_id, created_at);
CREATE INDEX ix_job_no_trip_reasons_job ON job_no_trip_reasons (job_id);

ALTER TABLE location_settings
    ADD COLUMN strict_trip_mode     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN trip_open_maps       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN travel_ping_seconds  INTEGER NOT NULL DEFAULT 120,
    ADD COLUMN on_site_ping_minutes INTEGER NOT NULL DEFAULT 60;

ALTER TABLE location_settings
    ADD CONSTRAINT location_settings_travel_ping_check CHECK (travel_ping_seconds BETWEEN 30 AND 600),
    ADD CONSTRAINT location_settings_on_site_ping_check CHECK (on_site_ping_minutes BETWEEN 15 AND 240);

ALTER TABLE technician_presence ADD COLUMN state VARCHAR(20);

ALTER TABLE technician_presence
    ADD CONSTRAINT technician_presence_state_check CHECK (state IS NULL OR state IN ('TRAVELLING', 'ON_SITE'));
