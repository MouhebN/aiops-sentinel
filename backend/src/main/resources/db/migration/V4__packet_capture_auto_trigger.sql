ALTER TABLE packet_capture_jobs
    ADD COLUMN IF NOT EXISTS capture_trigger VARCHAR(32) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE packet_capture_jobs
    ADD COLUMN IF NOT EXISTS pre_trigger_seconds INTEGER NOT NULL DEFAULT 0;

ALTER TABLE packet_capture_jobs
    ADD COLUMN IF NOT EXISTS post_trigger_seconds INTEGER NOT NULL DEFAULT 0;

UPDATE packet_capture_jobs
SET post_trigger_seconds = duration_seconds
WHERE post_trigger_seconds = 0 AND duration_seconds IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_packet_capture_jobs_auto_incident
    ON packet_capture_jobs (incident_id)
    WHERE capture_trigger IN ('AUTO_INCIDENT', 'AUTO_ROLLING');
