-- Latest position reported by a technician. One row per person.
CREATE TABLE technician_presence (
    user_id     UUID PRIMARY KEY REFERENCES users (id),
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    accuracy_m  DOUBLE PRECISION,
    job_id      UUID,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT technician_presence_lat_range CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT technician_presence_lng_range CHECK (lng BETWEEN -180 AND 180)
);
