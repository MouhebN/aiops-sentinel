package com.aiops.backend.incident;

import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentTimestampTests {

    @Test
    void firstSeenAtIsNotAfterLastSeenAtWhenEventsArriveOutOfOrder() {
        Instant first = Instant.parse("2026-08-18T15:10:00Z");
        Instant second = Instant.parse("2026-08-18T15:16:00Z");
        Incident incident = new Incident(
                "fw-ts:SECURITY",
                "SECURITY",
                event("later", second),
                DeviceStatus.WARNING,
                "security alert"
        );
        incident.addEvent(event("earlier", first));

        assertThat(incident.getFirstSeenAt()).isEqualTo(first);
        assertThat(incident.getLastSeenAt()).isEqualTo(second);
        assertThat(incident.getCreatedAt()).isEqualTo(second);
        assertThat(incident.getLastActivityAt()).isEqualTo(second);
        assertThat(incident.getFirstSeenAt()).isBeforeOrEqualTo(incident.getLastSeenAt());
    }

    @Test
    void recordActivityDoesNotMoveLastActivityAtBackwards() {
        Instant first = Instant.parse("2026-08-24T17:20:00Z");
        Instant later = Instant.parse("2026-08-24T17:25:00Z");
        Instant earlier = Instant.parse("2026-08-24T17:19:00Z");
        Incident incident = new Incident(
                "fw-activity:SECURITY",
                "SECURITY",
                event("first", first),
                DeviceStatus.WARNING,
                "security alert"
        );
        incident.acknowledge();
        Instant acknowledgedAt = incident.getAcknowledgedAt();

        incident.recordActivity(later);
        assertThat(incident.getLastActivityAt()).isEqualTo(later);
        assertThat(incident.getLastSeenAt()).isEqualTo(later);

        incident.recordActivity(earlier);
        assertThat(incident.getLastActivityAt()).isEqualTo(later);
        assertThat(incident.getLastSeenAt()).isEqualTo(later);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
        assertThat(incident.getAcknowledgedAt()).isEqualTo(acknowledgedAt);
    }

    private Event event(String suffix, Instant occurredAt) {
        return new Event(
                "fw-ts",
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "FIREWALL_DENY",
                Severity.WARNING,
                "DENY TCP 192.168.0.15:1 -> 192.168.0.1:22 " + suffix,
                "sourceAddress=192.168.0.15; destinationAddress=192.168.0.1; destinationPort=22",
                null,
                "192.168.0.15",
                "BANK-FW-01",
                "FIREWALL",
                "SYSLOG",
                occurredAt
        );
    }
}
