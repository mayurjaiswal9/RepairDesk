-- V11: staff-assisted intake (APB-SAI-002 / APB-SAI-003)
-- Origin + actor on the request; optional desk channel.

ALTER TABLE repair_requests
    ADD COLUMN origin varchar(30) NOT NULL DEFAULT 'CUSTOMER_APP',
    ADD COLUMN created_by uuid,
    ADD COLUMN intake_channel varchar(20);

ALTER TABLE repair_requests
    ADD CONSTRAINT fk_requests_created_by
        FOREIGN KEY (created_by) REFERENCES users (id);

ALTER TABLE repair_requests
    ADD CONSTRAINT requests_origin_check
        CHECK (origin IN ('CUSTOMER_APP', 'STAFF_ASSISTED'));

ALTER TABLE repair_requests
    ADD CONSTRAINT requests_intake_channel_check
        CHECK (intake_channel IS NULL OR intake_channel IN ('PHONE', 'WALK_IN', 'WHATSAPP', 'OTHER'));
