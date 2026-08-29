-- Hibernate ddl-auto=update created this check from the original IncidentStatus enum:
--   CHECK (status IN ('ACTIVE', 'RECOVERED'))
-- Schema update does not rewrite CHECK constraints when enum values change.
-- Drop first: UPDATE to RESOLVED would itself violate the old check.

DO $$
BEGIN
    IF to_regclass('public.incidents') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE incidents DROP CONSTRAINT IF EXISTS incidents_status_check;

    UPDATE incidents
    SET status = 'RESOLVED'
    WHERE status = 'RECOVERED';

    ALTER TABLE incidents
        ADD CONSTRAINT incidents_status_check
        CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED'));
END
$$;
