-- V3: jobs — active repair lifecycle, 1:1 with an approved request.
-- See docs/Database.md §3.4. The current_appointment_id FK is added in V4
-- (after the appointments table exists).

CREATE TABLE jobs (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number            varchar(30) NOT NULL,
    request_id                  uuid        NOT NULL,
    customer_id                 uuid        NOT NULL,
    device_category_id          uuid,
    assigned_technician_id      uuid,
    current_appointment_id      uuid,
    status                      varchar(20) NOT NULL DEFAULT 'CREATED',
    priority                    varchar(10) NOT NULL DEFAULT 'NORMAL',
    issue_description           text        NOT NULL,
    device_brand                varchar(100),
    device_model                varchar(100),
    device_serial               varchar(100),
    purchase_date               date,
    warranty_status             varchar(20),
    warranty_confirmed_by_staff varchar(20),
    resolution_summary          text,
    parts_needed_note           text,
    cancellation_reason         text,
    completed_at                timestamptz,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz,
    version                     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ux_jobs_reference UNIQUE (reference_number),
    CONSTRAINT ux_jobs_request   UNIQUE (request_id),
    CONSTRAINT fk_jobs_request         FOREIGN KEY (request_id)             REFERENCES repair_requests (id),
    CONSTRAINT fk_jobs_customer        FOREIGN KEY (customer_id)            REFERENCES users (id),
    CONSTRAINT fk_jobs_device_category FOREIGN KEY (device_category_id)     REFERENCES device_categories (id),
    CONSTRAINT fk_jobs_technician      FOREIGN KEY (assigned_technician_id) REFERENCES users (id),
    CONSTRAINT jobs_status_check    CHECK (status   IN ('CREATED', 'ASSIGNED', 'SCHEDULED', 'IN_PROGRESS', 'AWAITING_PARTS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT jobs_priority_check  CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT jobs_warranty_check  CHECK (warranty_status IS NULL OR warranty_status IN ('IN_WARRANTY', 'OUT_OF_WARRANTY', 'UNKNOWN')),
    CONSTRAINT jobs_warranty_confirmed_check CHECK (warranty_confirmed_by_staff IS NULL OR warranty_confirmed_by_staff IN ('IN_WARRANTY', 'OUT_OF_WARRANTY', 'UNKNOWN'))
);

CREATE INDEX ix_jobs_status              ON jobs (status);
CREATE INDEX ix_jobs_technician_status   ON jobs (assigned_technician_id, status);
CREATE INDEX ix_jobs_queue               ON jobs (priority, created_at);
