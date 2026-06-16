-- V5: attachments (polymorphic), notifications, audit_events (append-only).
-- See docs/Database.md §3.6, §3.7, §3.8.

-- ---------------------------------------------------------------------------
-- attachments — image metadata; binary lives on the file system, not the DB.
-- Polymorphic owner (REPAIR_REQUEST | JOB); no DB FK on owner_id by design.
-- ---------------------------------------------------------------------------
CREATE TABLE attachments (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type        varchar(20)  NOT NULL,
    owner_id          uuid         NOT NULL,
    uploaded_by       uuid         NOT NULL,
    content_type      varchar(100) NOT NULL,
    file_size_bytes   bigint       NOT NULL,
    original_filename varchar(255),
    storage_key       varchar(255) NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    version           bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ux_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_attachments_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT attachments_owner_type_check CHECK (owner_type IN ('REPAIR_REQUEST', 'JOB'))
);

CREATE INDEX ix_attachments_owner ON attachments (owner_type, owner_id);

-- ---------------------------------------------------------------------------
-- notifications — persisted customer inbox / technician feed
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id     uuid        NOT NULL,
    type             varchar(40) NOT NULL,
    message          text        NOT NULL,
    related_type     varchar(20),
    related_id       uuid,
    reference_number varchar(30),
    read             boolean     NOT NULL DEFAULT false,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id),
    CONSTRAINT notifications_related_type_check CHECK (related_type IS NULL OR related_type IN ('REPAIR_REQUEST', 'JOB', 'APPOINTMENT'))
);

CREATE INDEX ix_notifications_inbox  ON notifications (recipient_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (recipient_id, read);

-- ---------------------------------------------------------------------------
-- audit_events — append-only, polymorphic journal
-- ---------------------------------------------------------------------------
CREATE TABLE audit_events (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type    varchar(40) NOT NULL,
    entity_id      uuid        NOT NULL,
    action         varchar(60) NOT NULL,
    actor_id       uuid,
    actor_username varchar(50),
    before_data    jsonb,
    after_data     jsonb,
    reason         text,
    correlation_id varchar(64),
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);

CREATE INDEX ix_audit_entity ON audit_events (entity_type, entity_id, created_at);
CREATE INDEX ix_audit_actor  ON audit_events (actor_id);
CREATE INDEX ix_audit_action ON audit_events (action);
