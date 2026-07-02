-- V8: Performance indexes (PERF-001).
-- Covers the most common query paths not already indexed in V1–V7.

-- ── repair_requests ──────────────────────────────────────────────────────────

-- Reference-number lookups (TrackingService, JobService.approve)
CREATE UNIQUE INDEX ux_requests_reference_number ON repair_requests (reference_number);

-- Customer ↔ updated-at for the tracking list (ordered desc)
CREATE INDEX ix_requests_customer_updated ON repair_requests (customer_id, updated_at DESC);

-- ── jobs ─────────────────────────────────────────────────────────────────────

-- Reference-number lookups (job detail, tracking)
CREATE UNIQUE INDEX ux_jobs_reference_number ON jobs (reference_number);

-- Customer history (JobRepository.findByCustomerId)
CREATE INDEX ix_jobs_customer ON jobs (customer_id);

-- Request → job join (JobRepository.findByRequestId, approve)
CREATE UNIQUE INDEX ux_jobs_request_id ON jobs (request_id);

-- ── appointments ─────────────────────────────────────────────────────────────

-- Calendar range queries (AppointmentRepository.findAllInRange)
CREATE INDEX ix_appointments_date_range ON appointments (scheduled_date, status);

-- ── audit_events ─────────────────────────────────────────────────────────────
-- Already has ix_audit_entity; add a composite for common range query
CREATE INDEX ix_audit_entity_date ON audit_events (entity_type, entity_id, created_at DESC);

-- ── notifications ─────────────────────────────────────────────────────────────
-- Already has ix_notifications_inbox; complement for unread count scans
CREATE INDEX ix_notifications_unread_count ON notifications (recipient_id, read) WHERE read = false;
