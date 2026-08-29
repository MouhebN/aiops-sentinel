package com.aiops.backend.incident;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentStatusCheckMigrationTests {

    @Test
    void v1DropsLegacyCheckBeforeMigratingRecoveredAndAllowsResolved() throws IOException {
        String sql = readMigration("db/migration/V1__align_incidents_status_check.sql");

        int dropAt = sql.indexOf("DROP CONSTRAINT IF EXISTS incidents_status_check");
        int updateAt = sql.indexOf("SET status = 'RESOLVED'");
        int recoveredFilterAt = sql.indexOf("WHERE status = 'RECOVERED'");
        int addAt = sql.indexOf("ADD CONSTRAINT incidents_status_check");

        assertThat(dropAt).isGreaterThanOrEqualTo(0);
        assertThat(updateAt).isGreaterThan(dropAt);
        assertThat(recoveredFilterAt).isGreaterThan(updateAt);
        assertThat(addAt).isGreaterThan(recoveredFilterAt);

        String newCheck = sql.substring(addAt);
        assertThat(newCheck).contains("'ACTIVE'");
        assertThat(newCheck).contains("'ACKNOWLEDGED'");
        assertThat(newCheck).contains("'RESOLVED'");
        assertThat(newCheck).doesNotContain("RECOVERED");
        assertThat(sql).contains("to_regclass('public.incidents')");
    }

    private static String readMigration(String classpathLocation) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathLocation)) {
            assertThat(in).as("missing classpath resource %s", classpathLocation).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
