package com.aiops.backend.topology;

import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyIpExtractorTests {

    @Test
    void extractsLabeledSourceAndDestinationFromEventDetails() {
        Event event = event(
                "protocol=TCP; sourceAddress=10.0.0.10; destinationAddress=10.10.10.20; destinationPort=22",
                "DENY TCP 10.0.0.10:45122 -> 10.10.10.20:22"
        );

        assertThat(TopologyIpExtractor.sourceIp(event)).isEqualTo("10.0.0.10");
        assertThat(TopologyIpExtractor.destinationIp(event)).isEqualTo("10.10.10.20");
    }

    @Test
    void extractsFirstIpFromPcapSummaryText() {
        assertThat(TopologyIpExtractor.firstIp("10.0.0.10 (12 packets)")).isEqualTo("10.0.0.10");
        assertThat(TopologyIpExtractor.firstIp("no addresses")).isNull();
    }

    private Event event(String details, String rawLog) {
        return new Event(
                "fw-1",
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "FIREWALL_DENY",
                Severity.CRITICAL,
                "denied",
                details,
                rawLog,
                "10.0.0.10",
                "BANK-FW-01",
                "FIREWALL",
                "SYSLOG",
                Instant.parse("2026-08-23T12:00:00Z")
        );
    }
}
