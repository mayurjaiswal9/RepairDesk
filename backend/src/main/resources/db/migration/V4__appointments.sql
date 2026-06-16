-- V4: appointments — scheduling records (child of job), plus the deferred
-- jobs.current_appointment_id FK. See docs/Database.md §3.5.

CREATE TABLE appointments (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id                   uuid        NOT NULL,
    technician_id            uuid        NOT NULL,
    scheduled_date           date        NOT NULL,
    start_time               time        NOT NULL,
    end_time                 time        NOT NULL,
    status                   varchar(20) NOT NULL DEFAULT 'SCHEDULED',
    customer_requested_date  date,
    customer_requested_time  time,
    appointment_notes        text,
    cancellation_reason      text,
    supersedes_appointment_id uuid,
    created_by               uuid        NOT NULL,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz,
    version                  bigint      NOT NULL DEFAULT 0,
    CONSTRAINT fk_appointments_job        FOREIGN KEY (job_id)        REFERENCES jobs (id),
    CONSTRAINT fk_appointments_technician FOREIGN KEY (technician_id) REFERENCES users (id),
    CONSTRAINT fk_appointments_created_by FOREIGN KEY (created_by)    REFERENCES users (id),
    CONSTRAINT fk_appointments_supersedes FOREIGN KEY (supersedes_appointment_id) REFERENCES appointments (id),
    CONSTRAINT appointments_status_check CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX ix_appointments_tech_date   ON appointments (technician_id, scheduled_date);
CREATE INDEX ix_appointments_job         ON appointments (job_id);
CREATE INDEX ix_appointments_status_date ON appointments (status, scheduled_date);

-- Now that appointments exists, wire the active-appointment pointer on jobs.
ALTER TABLE jobs
    ADD CONSTRAINT fk_jobs_current_appointment
    FOREIGN KEY (current_appointment_id) REFERENCES appointments (id);
