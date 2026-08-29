package com.aiops.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class H2EnumColumnMigration implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2EnumColumnMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public H2EnumColumnMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAppUsersUpdatedAtColumn();
        ensureAuditLogsActionColumn();
        ensureSyslogExpectedHostNotUnique();
        migrateEnumColumn("devices", "type");
        migrateEnumColumn("events", "device_type");
        migrateEnumColumn("diagnostic_reports", "device_type");
        migrateEnumColumn("monitored_components", "type");
        migrateEnumColumn("component_monitoring_methods", "method");
        migrateEnumColumn("incidents", "device_type");
        migrateEnumColumn("incidents", "severity");
        migrateEnumColumn("incidents", "status");
        migrateEnumColumn("incidents", "resulting_status");
        migrateEnumColumn("component_network_interfaces", "role");
        migrateEnumColumn("netflow_sources", "provider_type");
        migrateEnumColumn("netflow_sources", "last_import_status");
        migrateEnumColumn("network_flows", "anomaly_type");
        migrateEnumColumn("netflow_import_runs", "status");
        migrateEnumColumn("packet_capture_jobs", "status");
        migrateEnumColumn("packet_capture_jobs", "failure_code");
        migrateEnumColumn("packet_capture_jobs", "capture_trigger");
        ensurePacketCaptureJobTriggerColumns();
        migrateEnumColumn("syslog_sources", "device_type");
        migrateEnumColumn("syslog_sources", "parser_profile");
        migrateEventColumn("raw_log", "TEXT");
        migrateEventColumn("source_ip", "VARCHAR(120)");
        migrateEventColumn("syslog_source_name", "VARCHAR(120)");
        migrateEventColumn("parsing_profile", "VARCHAR(60)");
        migrateEventColumn("event_source", "VARCHAR(40)");
        migrateDiagnosticReportColumn("packet_capture_summaries_text", "TEXT");
        migrateDiagnosticReportColumn("network_flow_summaries_text", "TEXT");
    }

    private void ensureAppUsersUpdatedAtColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE app_users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE");
            jdbcTemplate.execute("UPDATE app_users SET updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)");
            jdbcTemplate.execute("ALTER TABLE app_users ALTER COLUMN updated_at SET NOT NULL");
            LOGGER.info("Ensured app_users.updated_at exists and is populated");
        } catch (Exception exception) {
            LOGGER.debug("Skipping app_users.updated_at migration: {}", exception.getMessage());
        }
    }

    private void ensureAuditLogsActionColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_action_check");
            jdbcTemplate.execute("ALTER TABLE audit_logs ALTER COLUMN action TYPE VARCHAR(255)");
            LOGGER.info("Ensured audit_logs.action accepts the current audit action set");
        } catch (Exception exception) {
            LOGGER.debug("Skipping audit_logs.action migration: {}", exception.getMessage());
        }
    }

    private void ensureSyslogExpectedHostNotUnique() {
        try {
            jdbcTemplate.execute("ALTER TABLE syslog_sources DROP CONSTRAINT IF EXISTS uk3vw5shatpnogbalmv8r4ok4m4");
        } catch (Exception exception) {
            LOGGER.debug("Skipping named syslog_sources expected_host constraint drop: {}", exception.getMessage());
        }

        try {
            jdbcTemplate.queryForList(
                            """
                            SELECT constraint_name
                            FROM information_schema.constraint_column_usage
                            WHERE table_name = 'syslog_sources'
                              AND column_name = 'expected_host'
                            """
                    )
                    .forEach(row -> {
                        Object constraintName = row.get("constraint_name");
                        if (constraintName != null) {
                            try {
                                jdbcTemplate.execute("ALTER TABLE syslog_sources DROP CONSTRAINT IF EXISTS " + constraintName);
                            } catch (Exception exception) {
                                LOGGER.debug("Skipping syslog_sources constraint {} drop: {}", constraintName, exception.getMessage());
                            }
                        }
                    });
        } catch (Exception exception) {
            LOGGER.debug("Skipping syslog_sources expected_host constraint scan: {}", exception.getMessage());
        }

        try {
            jdbcTemplate.execute("DROP INDEX IF EXISTS uk3vw5shatpnogbalmv8r4ok4m4");
        } catch (Exception exception) {
            LOGGER.debug("Skipping syslog_sources expected_host index drop: {}", exception.getMessage());
        }
    }

    private void migrateEnumColumn(String table, String column) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " VARCHAR(255)");
            LOGGER.info("Ensured {}.{} is stored as VARCHAR", table, column);
        } catch (Exception exception) {
            LOGGER.debug("Skipping enum column migration for {}.{}: {}", table, column, exception.getMessage());
        }
    }

    private void migrateEventColumn(String column, String type) {
        try {
            jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS " + column + " " + type);
            LOGGER.info("Ensured events.{} exists", column);
        } catch (Exception exception) {
            LOGGER.debug("Skipping events.{} migration: {}", column, exception.getMessage());
        }
    }

    private void ensurePacketCaptureJobTriggerColumns() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE packet_capture_jobs ADD COLUMN IF NOT EXISTS capture_trigger VARCHAR(32) DEFAULT 'MANUAL'"
            );
            jdbcTemplate.execute("UPDATE packet_capture_jobs SET capture_trigger = 'MANUAL' WHERE capture_trigger IS NULL");
            jdbcTemplate.execute(
                    "ALTER TABLE packet_capture_jobs ADD COLUMN IF NOT EXISTS pre_trigger_seconds INTEGER DEFAULT 0"
            );
            jdbcTemplate.execute(
                    "ALTER TABLE packet_capture_jobs ADD COLUMN IF NOT EXISTS post_trigger_seconds INTEGER DEFAULT 0"
            );
            LOGGER.info("Ensured packet_capture_jobs capture trigger columns exist");
        } catch (Exception exception) {
            LOGGER.debug("Skipping packet_capture_jobs trigger columns: {}", exception.getMessage());
        }
    }

    private void migrateDiagnosticReportColumn(String column, String type) {
        try {
            jdbcTemplate.execute("ALTER TABLE diagnostic_reports ADD COLUMN IF NOT EXISTS " + column + " " + type);
            LOGGER.info("Ensured diagnostic_reports.{} exists", column);
        } catch (Exception exception) {
            LOGGER.debug("Skipping diagnostic_reports.{} migration: {}", column, exception.getMessage());
        }
    }
}
