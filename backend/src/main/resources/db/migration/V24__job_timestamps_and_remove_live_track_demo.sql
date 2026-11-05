-- Jobs that were never edited stored a null updated_at, which the portal
-- rendered as 31 Dec 1969. Created time is the update time until something changes.
UPDATE jobs SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE repair_requests SET updated_at = created_at WHERE updated_at IS NULL;

-- Remove the Live Track Demo job (RD-2026000018, customer gps.customer) and its request.
DO $$
DECLARE
    target_job uuid;
    target_request uuid;
BEGIN
    SELECT j.id, j.request_id
      INTO target_job, target_request
      FROM jobs j
      JOIN users u ON u.id = j.customer_id
     WHERE j.reference_number = 'RD-2026000018'
       AND u.username = 'gps.customer';

    IF target_job IS NULL THEN
        RETURN;
    END IF;

    UPDATE technician_presence SET job_id = NULL WHERE job_id = target_job;
    UPDATE job_trips SET start_job_id = NULL WHERE start_job_id = target_job;
    DELETE FROM job_no_trip_reasons WHERE job_id = target_job;
    DELETE FROM job_trips WHERE job_id = target_job;
    UPDATE jobs SET current_appointment_id = NULL WHERE id = target_job;
    UPDATE appointments
       SET supersedes_appointment_id = NULL
     WHERE supersedes_appointment_id IN (
         SELECT id FROM appointments WHERE job_id = target_job
     );
    DELETE FROM appointments WHERE job_id = target_job;
    DELETE FROM attachments
     WHERE (owner_type = 'JOB' AND owner_id = target_job)
        OR (owner_type = 'REPAIR_REQUEST' AND owner_id = target_request);
    DELETE FROM notifications
     WHERE related_id IN (target_job, target_request)
        OR reference_number = 'RD-2026000018';
    DELETE FROM jobs WHERE id = target_job;
    DELETE FROM repair_requests WHERE id = target_request;
END $$;
