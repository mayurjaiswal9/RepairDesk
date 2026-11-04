-- One company row for ACE Telecom. Existing rows gain company_id and nothing else changes.

CREATE TABLE companies (
    id          uuid PRIMARY KEY,
    code        varchar(40)  NOT NULL,
    name        varchar(150) NOT NULL,
    status      varchar(20)  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_companies_code UNIQUE (code),
    CONSTRAINT companies_status_check CHECK (status IN ('ACTIVE', 'DISABLED'))
);

INSERT INTO companies (id, code, name, status)
VALUES (gen_random_uuid(), 'ace', 'ACE Telecom', 'ACTIVE');

DO $$
DECLARE
    ace uuid;
    tbl text;
BEGIN
    SELECT id INTO ace FROM companies WHERE code = 'ace';
    FOREACH tbl IN ARRAY ARRAY[
        'users',
        'device_categories',
        'device_brands',
        'repair_requests',
        'jobs',
        'appointments',
        'attachments',
        'notifications',
        'audit_events',
        'technician_presence',
        'attendance_days',
        'working_hours',
        'business_settings',
        'location_settings'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN company_id uuid', tbl);
        EXECUTE format('UPDATE %I SET company_id = %L::uuid', tbl, ace);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN company_id SET NOT NULL', tbl);
        EXECUTE format(
            'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (company_id) REFERENCES companies (id)',
            tbl, 'fk_' || tbl || '_company');
        EXECUTE format('CREATE INDEX %I ON %I (company_id)', 'ix_' || tbl || '_company', tbl);
    END LOOP;
END $$;
