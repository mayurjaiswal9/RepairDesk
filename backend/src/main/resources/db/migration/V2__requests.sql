-- V2: repair_requests — customer intake + review lifecycle.
-- See docs/Database.md §3.3.

CREATE TABLE repair_requests (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number          varchar(30)  NOT NULL,
    customer_id               uuid         NOT NULL,
    device_category_id        uuid         NOT NULL,
    contact_name              varchar(100) NOT NULL,
    contact_mobile            varchar(20)  NOT NULL,
    contact_email             varchar(255),
    device_brand              varchar(100),
    device_model              varchar(100),
    device_serial             varchar(100),
    purchase_date             date,
    warranty_status           varchar(20),
    issue_description         text         NOT NULL,
    preferred_date            date         NOT NULL,
    preferred_time            time         NOT NULL,
    status                    varchar(30)  NOT NULL DEFAULT 'PENDING_REVIEW',
    priority                  varchar(10)  NOT NULL DEFAULT 'NORMAL',
    rejection_reason_code     varchar(40),
    rejection_reason_internal text,
    customer_facing_message   text,
    info_requested_message    text,
    submitted_at              timestamptz  NOT NULL DEFAULT now(),
    reviewed_at               timestamptz,
    reviewed_by               uuid,
    created_at                timestamptz  NOT NULL DEFAULT now(),
    updated_at                timestamptz,
    version                   bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ux_repair_requests_reference UNIQUE (reference_number),
    CONSTRAINT fk_requests_customer        FOREIGN KEY (customer_id)        REFERENCES users (id),
    CONSTRAINT fk_requests_device_category FOREIGN KEY (device_category_id) REFERENCES device_categories (id),
    CONSTRAINT fk_requests_reviewed_by     FOREIGN KEY (reviewed_by)        REFERENCES users (id),
    CONSTRAINT requests_status_check   CHECK (status   IN ('PENDING_REVIEW', 'INFORMATION_REQUIRED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT requests_priority_check CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT requests_warranty_check CHECK (warranty_status IS NULL OR warranty_status IN ('IN_WARRANTY', 'OUT_OF_WARRANTY', 'UNKNOWN'))
);

CREATE INDEX ix_requests_status            ON repair_requests (status);
CREATE INDEX ix_requests_customer          ON repair_requests (customer_id);
CREATE INDEX ix_requests_device_category   ON repair_requests (device_category_id);
CREATE INDEX ix_requests_queue             ON repair_requests (priority, submitted_at);
CREATE INDEX ix_requests_contact_name      ON repair_requests (lower(contact_name));
CREATE INDEX ix_requests_contact_mobile    ON repair_requests (contact_mobile);
