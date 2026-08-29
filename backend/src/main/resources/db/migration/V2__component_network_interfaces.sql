-- Hibernate ddl-auto=update also creates this table from ComponentNetworkInterface.
-- Flyway keeps indexes/constraints explicit on the live PostgreSQL volume.

CREATE TABLE IF NOT EXISTS component_network_interfaces (
    id BIGSERIAL PRIMARY KEY,
    monitored_component_id BIGINT NOT NULL REFERENCES monitored_components (id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_component_network_interfaces_ip
    ON component_network_interfaces (ip_address);

CREATE INDEX IF NOT EXISTS idx_component_network_interfaces_component
    ON component_network_interfaces (monitored_component_id);

CREATE INDEX IF NOT EXISTS idx_monitored_components_ip_address
    ON monitored_components (ip_address);
