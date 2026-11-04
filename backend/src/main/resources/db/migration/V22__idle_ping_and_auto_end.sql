-- Idle / on-site location pings every 30 minutes. Work day auto-ends at 20:00 IST.
-- Travel pings stay on travel_ping_seconds. Feature switches are not changed.
UPDATE location_settings
SET ping_interval_minutes = 30,
    day_auto_end_time = TIME '20:00:00',
    on_site_ping_minutes = 30;
