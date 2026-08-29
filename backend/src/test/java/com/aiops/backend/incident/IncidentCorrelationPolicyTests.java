package com.aiops.backend.incident;

import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentCorrelationPolicyTests {

    private final IncidentCorrelationPolicy policy = new IncidentCorrelationPolicy(properties(), null);

    @Test
    void identityOfStripsGenerationSuffix() {
        assertThat(IncidentCorrelationPolicy.identityOf("fw-01:SECURITY")).isEqualTo("fw-01:SECURITY");
        assertThat(IncidentCorrelationPolicy.identityOf("fw-01:SECURITY:g12")).isEqualTo("fw-01:SECURITY");
        assertThat(IncidentCorrelationPolicy.identityOf("NETFLOW:PORT_SCAN:10.0.0.10:10.10.10.20:g3"))
                .isEqualTo("NETFLOW:PORT_SCAN:10.0.0.10:10.10.10.20");
        assertThat(IncidentCorrelationPolicy.identityOf("fw-01:SECURITY:gen")).isEqualTo("fw-01:SECURITY:gen");
    }

    @Test
    void activeIncidentIsReusableWithinThirtyMinutesOfLastActivity() {
        Instant created = Instant.parse("2026-08-24T16:00:00Z");
        Incident incident = portScan(created);

        assertThat(policy.canReuse(incident, Instant.parse("2026-08-24T16:20:00Z"))).isTrue();
        assertThat(policy.canReuse(incident, Instant.parse("2026-08-24T16:30:00Z"))).isTrue();
        assertThat(policy.canReuse(incident, Instant.parse("2026-08-24T16:30:01Z"))).isFalse();
    }

    @Test
    void acknowledgedIncidentRemainsReusableInsideTheWindow() {
        Instant created = Instant.parse("2026-08-24T16:00:00Z");
        Incident incident = portScan(created);
        incident.acknowledge();

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
        assertThat(policy.canReuse(incident, Instant.parse("2026-08-24T16:10:00Z"))).isTrue();
        assertThat(policy.canReuse(incident, Instant.parse("2026-08-24T18:00:00Z"))).isFalse();
    }

    @Test
    void resolvedIncidentIsNeverReusable() {
        Instant created = Instant.parse("2026-08-24T16:00:00Z");
        Incident incident = portScan(created);
        incident.resolve();

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(policy.canReuse(incident, Instant.parse("2026-08-24T16:01:00Z"))).isFalse();
    }

    private static IncidentProperties properties() {
        IncidentProperties properties = new IncidentProperties();
        properties.setCorrelationInactivityMinutes(30L);
        return properties;
    }

    private static Incident portScan(Instant occurredAt) {
        return new Incident(
                "fw-policy:SECURITY",
                "SECURITY",
                new Event(
                        "fw-policy",
                        "BANK-FW-01",
                        DeviceType.FIREWALL,
                        "lab",
                        "POSSIBLE_PORT_SCAN",
                        Severity.CRITICAL,
                        "Possible port scan",
                        "sourceAddress=10.0.0.10; destinationAddress=10.10.10.20; anomalyType=PORT_SCAN",
                        null,
                        "10.0.0.10",
                        "BANK-FW-01",
                        "FIREWALL",
                        "SYSLOG",
                        occurredAt
                ),
                DeviceStatus.WARNING,
                "possible port scan detected"
        );
    }
}
