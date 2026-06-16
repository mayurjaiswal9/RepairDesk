-- V1: Core accounts, device catalog, business settings, working hours, reference sequence.
-- See docs/Database.md §3.1, §3.2, §3.9, §3.10, §6.

-- ---------------------------------------------------------------------------
-- users — accounts for all roles
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username             varchar(50)  NOT NULL,
    mobile               varchar(20)  NOT NULL,
    name                 varchar(100),
    password_hash        varchar(100) NOT NULL,
    role                 varchar(20)  NOT NULL,
    status               varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password boolean      NOT NULL DEFAULT false,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz,
    version              bigint       NOT NULL DEFAULT 0,
    CONSTRAINT users_role_check   CHECK (role   IN ('CUSTOMER', 'TECHNICIAN', 'STAFF', 'ADMIN')),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED'))
);

-- Partial uniqueness: identifiers free up again after a logical delete.
CREATE UNIQUE INDEX ux_users_username_active ON users (lower(username)) WHERE status <> 'DELETED';
CREATE UNIQUE INDEX ux_users_mobile_active   ON users (mobile)          WHERE status <> 'DELETED';

-- Drives the active-technician roster and role/status filters.
CREATE INDEX ix_users_role_status ON users (role, status);
-- Simple search support (ILIKE-based search per resolved decision; no pg_trgm in V1).
CREATE INDEX ix_users_name_lower  ON users (lower(name));

-- ---------------------------------------------------------------------------
-- device_categories — admin-managed device/product catalog
-- ---------------------------------------------------------------------------
CREATE TABLE device_categories (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(100) NOT NULL,
    description varchar(500),
    archived    boolean      NOT NULL DEFAULT false,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    version     bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_device_categories_name_active ON device_categories (lower(name)) WHERE archived = false;
CREATE INDEX ix_device_categories_archived ON device_categories (archived);

-- ---------------------------------------------------------------------------
-- business_settings — singleton configuration row
-- ---------------------------------------------------------------------------
CREATE TABLE business_settings (
    id                    smallint PRIMARY KEY DEFAULT 1,
    business_name         varchar(150) DEFAULT 'ACE Telecom',
    business_address      varchar(500),
    business_phone        varchar(20),
    business_email        varchar(255),
    slot_duration_minutes int    NOT NULL DEFAULT 60,
    booking_horizon_days  int    NOT NULL DEFAULT 60,
    updated_at            timestamptz,
    version               bigint NOT NULL DEFAULT 0,
    CONSTRAINT business_settings_singleton_check CHECK (id = 1),
    CONSTRAINT business_settings_slot_check      CHECK (slot_duration_minutes IN (30, 60, 90, 120))
);

-- ---------------------------------------------------------------------------
-- working_hours — weekly recurring business calendar (0=Mon .. 6=Sun)
-- ---------------------------------------------------------------------------
CREATE TABLE working_hours (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    day_of_week smallint NOT NULL,
    is_closed   boolean  NOT NULL DEFAULT false,
    open_time   time,
    close_time  time,
    version     bigint   NOT NULL DEFAULT 0,
    CONSTRAINT working_hours_dow_check CHECK (day_of_week BETWEEN 0 AND 6),
    CONSTRAINT ux_working_hours_dow UNIQUE (day_of_week)
);

-- ---------------------------------------------------------------------------
-- Repair reference number sequence (formatted by the service as RD-YYYYNNNNNN)
-- ---------------------------------------------------------------------------
CREATE SEQUENCE repair_reference_seq START 1 INCREMENT 1;
