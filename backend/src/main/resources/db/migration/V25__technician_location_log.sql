-- Each shared position, kept for two days. The board still uses technician_presence
-- for the latest point. This log is what the Locations tab lists by day.

CREATE TABLE technician_location_log (
    id           UUID PRIMARY KEY,
    company_id   UUID NOT NULL REFERENCES companies (id),
    user_id      UUID NOT NULL REFERENCES users (id),
    lat          DOUBLE PRECISION NOT NULL,
    lng          DOUBLE PRECISION NOT NULL,
    accuracy_m   DOUBLE PRECISION,
    job_id       UUID,
    state        VARCHAR(20),
    recorded_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT technician_location_log_state_check CHECK (
        state IS NULL OR state IN ('TRAVELLING', 'ON_SITE')),
    CONSTRAINT technician_location_log_lat_range CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT technician_location_log_lng_range CHECK (lng BETWEEN -180 AND 180)
);

CREATE INDEX ix_technician_location_log_company_recorded
    ON technician_location_log (company_id, recorded_at DESC);
CREATE INDEX ix_technician_location_log_user_recorded
    ON technician_location_log (user_id, recorded_at DESC);

-- The current pin becomes the first history row, so today's list is not empty.
INSERT INTO technician_location_log (
    id, company_id, user_id, lat, lng, accuracy_m, job_id, state, recorded_at, created_at, updated_at, version)
SELECT gen_random_uuid(), company_id, user_id, lat, lng, accuracy_m, job_id, state, recorded_at, now(), now(), 0
FROM technician_presence;
