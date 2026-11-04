-- One work day per technician per India date. Coordinates stay here for the
-- start and end flag. The staff list does not return them.
CREATE TABLE attendance_days (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    technician_id           UUID NOT NULL REFERENCES users (id),
    work_date               DATE NOT NULL,
    start_at                TIMESTAMPTZ NOT NULL,
    end_at                  TIMESTAMPTZ,
    start_flag              VARCHAR(20) NOT NULL,
    end_flag                VARCHAR(20),
    end_reason              VARCHAR(20),
    start_lat               DOUBLE PRECISION,
    start_lng               DOUBLE PRECISION,
    start_accuracy_m        DOUBLE PRECISION,
    end_lat                 DOUBLE PRECISION,
    end_lng                 DOUBLE PRECISION,
    end_accuracy_m          DOUBLE PRECISION,
    privacy_notice_version  VARCHAR(20) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_attendance_days_technician_date UNIQUE (technician_id, work_date),
    CONSTRAINT attendance_days_start_flag_check CHECK (
        start_flag IN ('INSIDE', 'OUTSIDE', 'UNCERTAIN', 'MISSING', 'MOCKED')),
    CONSTRAINT attendance_days_end_flag_check CHECK (
        end_flag IS NULL OR end_flag IN ('INSIDE', 'OUTSIDE', 'UNCERTAIN', 'MISSING', 'MOCKED')),
    CONSTRAINT attendance_days_end_reason_check CHECK (
        end_reason IS NULL OR end_reason IN ('MANUAL', 'AUTO')),
    CONSTRAINT attendance_days_start_pair CHECK ((start_lat IS NULL) = (start_lng IS NULL)),
    CONSTRAINT attendance_days_end_pair CHECK ((end_lat IS NULL) = (end_lng IS NULL)),
    CONSTRAINT attendance_days_start_lat_range CHECK (start_lat IS NULL OR start_lat BETWEEN -90 AND 90),
    CONSTRAINT attendance_days_start_lng_range CHECK (start_lng IS NULL OR start_lng BETWEEN -180 AND 180),
    CONSTRAINT attendance_days_end_lat_range CHECK (end_lat IS NULL OR end_lat BETWEEN -90 AND 90),
    CONSTRAINT attendance_days_end_lng_range CHECK (end_lng IS NULL OR end_lng BETWEEN -180 AND 180)
);

CREATE INDEX ix_attendance_days_work_date ON attendance_days (work_date);
