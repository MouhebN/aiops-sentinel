CREATE TABLE IF NOT EXISTS packet_capture_jobs (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    provider VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_ip VARCHAR(64),
    destination_ip VARCHAR(64),
    capture_point_id VARCHAR(120),
    capture_point VARCHAR(160),
    interface_name VARCHAR(32),
    duration_seconds INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    packet_count INTEGER,
    file_size_bytes BIGINT,
    failure_code VARCHAR(40),
    error_message VARCHAR(1000),
    analysis_id BIGINT,
    provider_capture_id VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_packet_capture_jobs_incident
    ON packet_capture_jobs (incident_id, created_at DESC);
