-- V7: Add snapshot / internal fields to jobs table that were added in Phase 3 entities.
-- contact_name and contact_mobile are copied from the request at approval time.
-- warranty_confirmed is the staff boolean flag (simpler than warranty_confirmed_by_staff varchar).
-- internal_notes is technician-only.

ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS contact_name  varchar(100),
    ADD COLUMN IF NOT EXISTS contact_mobile varchar(20),
    ADD COLUMN IF NOT EXISTS internal_notes text,
    ADD COLUMN IF NOT EXISTS warranty_confirmed boolean NOT NULL DEFAULT false;
