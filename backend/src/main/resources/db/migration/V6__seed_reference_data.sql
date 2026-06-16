-- V6: Idempotent seed of reference data (business settings, working hours,
-- starter device categories). See docs/Database.md §9.

-- Singleton business settings row.
INSERT INTO business_settings (id, business_name, slot_duration_minutes, booking_horizon_days)
VALUES (1, 'ACE Telecom', 60, 60)
ON CONFLICT (id) DO NOTHING;

-- Weekly working hours: Mon–Sat 09:00–18:00 open, Sun closed (0=Mon .. 6=Sun).
INSERT INTO working_hours (day_of_week, is_closed, open_time, close_time) VALUES
    (0, false, '09:00', '18:00'),
    (1, false, '09:00', '18:00'),
    (2, false, '09:00', '18:00'),
    (3, false, '09:00', '18:00'),
    (4, false, '09:00', '18:00'),
    (5, false, '09:00', '18:00'),
    (6, true,  NULL,    NULL)
ON CONFLICT (day_of_week) DO NOTHING;

-- Starter active device categories (admin can add/edit/archive later).
INSERT INTO device_categories (name)
SELECT v.name
FROM (VALUES ('Mobile Phone'), ('Laptop'), ('Tablet'), ('Camera')) AS v(name)
WHERE NOT EXISTS (
    SELECT 1 FROM device_categories dc
    WHERE lower(dc.name) = lower(v.name) AND dc.archived = false
);
