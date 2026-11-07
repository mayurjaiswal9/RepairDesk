-- Per-company notification rules, phone tokens, and browser push subscriptions.
-- WhatsApp and SMS stay off until those channels are connected.

CREATE TABLE notification_rules (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id           uuid         NOT NULL,
    event_key            varchar(40)  NOT NULL,
    enabled              boolean      NOT NULL,
    message_template     text         NOT NULL,
    notify_customer      boolean      NOT NULL DEFAULT false,
    notify_technician    boolean      NOT NULL DEFAULT false,
    notify_staff         boolean      NOT NULL DEFAULT false,
    notify_admin         boolean      NOT NULL DEFAULT false,
    channel_in_app       boolean      NOT NULL DEFAULT true,
    channel_phone        boolean      NOT NULL DEFAULT false,
    channel_desktop      boolean      NOT NULL DEFAULT false,
    channel_whatsapp     boolean      NOT NULL DEFAULT false,
    channel_sms          boolean      NOT NULL DEFAULT false,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz,
    version              bigint       NOT NULL DEFAULT 0,
    CONSTRAINT fk_notification_rules_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT ux_notification_rules_company_event UNIQUE (company_id, event_key),
    CONSTRAINT notification_rules_external_off CHECK (channel_whatsapp = false AND channel_sms = false)
);

CREATE INDEX ix_notification_rules_company ON notification_rules (company_id);

CREATE TABLE notification_rule_users (
    rule_id uuid NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT pk_notification_rule_users PRIMARY KEY (rule_id, user_id),
    CONSTRAINT fk_notification_rule_users_rule FOREIGN KEY (rule_id) REFERENCES notification_rules (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_rule_users_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE device_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  uuid         NOT NULL,
    user_id     uuid         NOT NULL,
    token       varchar(512) NOT NULL,
    platform    varchar(20)  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT fk_device_tokens_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ux_device_tokens_token UNIQUE (token)
);

CREATE INDEX ix_device_tokens_user ON device_tokens (user_id);

CREATE TABLE web_push_subscriptions (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  uuid        NOT NULL,
    user_id     uuid        NOT NULL,
    endpoint    text        NOT NULL,
    p256dh      varchar(255) NOT NULL,
    auth_key    varchar(255) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    version     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT fk_web_push_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_web_push_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ux_web_push_endpoint UNIQUE (endpoint)
);

CREATE INDEX ix_web_push_user ON web_push_subscriptions (user_id);

INSERT INTO notification_rules (
    company_id, event_key, enabled, message_template,
    notify_customer, notify_technician, notify_staff, notify_admin,
    channel_in_app, channel_phone, channel_desktop
)
SELECT c.id, v.event_key, v.enabled, v.message_template,
       v.notify_customer, v.notify_technician, v.notify_staff, v.notify_admin,
       v.channel_in_app, v.channel_phone, v.channel_desktop
FROM companies c
CROSS JOIN (VALUES
    ('REQUEST_SUBMITTED'::varchar, true,
        'New repair request {reference} was submitted.'::text,
        false, false, true, true, true, true, true),
    ('REQUEST_RECEIVED', true,
        'We received your repair request {reference}.',
        true, false, false, false, true, true, false),
    ('JOB_APPROVED', true,
        'Your repair request {reference} has been accepted and is being prepared.',
        true, false, false, false, true, true, false),
    ('REQUEST_REJECTED', true,
        '{message}',
        true, false, false, false, true, true, false),
    ('INFO_REQUIRED', true,
        'More information is needed for your request {reference}: {message}',
        true, false, false, false, true, true, false),
    ('JOB_ASSIGNED', true,
        'You have been assigned to job {reference}.',
        false, true, false, false, true, true, true),
    ('JOB_UNASSIGNED', false,
        'You are no longer assigned to job {reference}.',
        false, true, false, false, true, true, true),
    ('JOB_IN_PROGRESS', true,
        'Work has started on your repair {reference}.',
        true, false, false, false, true, true, false),
    ('JOB_AWAITING_PARTS', true,
        'Your repair {reference} is on hold while we source parts.',
        true, false, false, false, true, true, false),
    ('JOB_RESUMED', false,
        'Work has resumed on your repair {reference}.',
        true, false, false, false, true, true, false),
    ('JOB_COMPLETED', true,
        'Your repair {reference} is complete and ready for collection.',
        true, false, false, false, true, true, false),
    ('JOB_CANCELLED', true,
        'Your repair {reference} has been cancelled. Reason: {reason}',
        true, false, false, false, true, true, false),
    ('APPOINTMENT_SCHEDULED', true,
        'Your repair appointment is scheduled for {date} at {time}.',
        true, false, false, false, true, true, false),
    ('APPOINTMENT_RESCHEDULED', true,
        'Your appointment has been rescheduled to {date} at {time}.',
        true, false, false, false, true, true, false),
    ('APPOINTMENT_CANCELLED', true,
        'Your appointment on {date} has been cancelled. {reason}',
        true, false, false, false, true, true, false),
    ('APPOINTMENT_SCHEDULED_TECHNICIAN', false,
        'You have an appointment for {reference} on {date} at {time}.',
        false, true, false, false, true, true, true),
    ('APPOINTMENT_RESCHEDULED_TECHNICIAN', false,
        'Your appointment for {reference} moved to {date} at {time}.',
        false, true, false, false, true, true, true),
    ('APPOINTMENT_CANCELLED_TECHNICIAN', false,
        'The appointment for {reference} on {date} was cancelled.',
        false, true, false, false, true, true, true),
    ('DAY_ENDED', true,
        'Your work day ended automatically at {when}.',
        false, true, false, false, true, true, true)
) AS v(event_key, enabled, message_template,
       notify_customer, notify_technician, notify_staff, notify_admin,
       channel_in_app, channel_phone, channel_desktop);
