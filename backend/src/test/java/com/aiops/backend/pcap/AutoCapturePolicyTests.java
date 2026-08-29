package com.aiops.backend.pcap;

import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.netflow.NetFlowAnomalyType;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoCapturePolicyTests {

    private final AutoCapturePolicy policy = new AutoCapturePolicy(new PcapCaptureProperties());

    @Test
    void portScanSecurityIncidentIsEligible() {
        Incident incident = incident("SECURITY", "POSSIBLE_PORT_SCAN", Severity.CRITICAL, "possible port scan detected");
        assertThat(policy.shouldCapture(incident)).isTrue();
        assertThat(policy.triggerType()).isEqualTo(CaptureTrigger.AUTO_ROLLING);
        assertThat(policy.preTriggerSeconds()).isEqualTo(60);
        assertThat(policy.postTriggerSeconds()).isEqualTo(20);
    }

    @Test
    void availabilityIncidentIsNotEligibleByDefault() {
        Incident incident = incident("AVAILABILITY", "PING_UNREACHABLE", Severity.CRITICAL, "host down");
        assertThat(policy.shouldCapture(incident)).isFalse();
    }

    @Test
    void resolvedIncidentIsNotEligible() {
        Incident incident = incident("SECURITY", "POSSIBLE_PORT_SCAN", Severity.CRITICAL, "possible port scan detected");
        incident.resolve();
        assertThat(policy.shouldCapture(incident)).isFalse();
    }

    @Test
    void firewallDenyWithoutScanTokenIsNotEligible() {
        Incident incident = incident("SECURITY", "FIREWALL_DENY", Severity.WARNING, "security alert");
        assertThat(policy.shouldCapture(incident)).isFalse();
    }

    @Test
    void genericTitleIsEligibleWhenNetFlowPortScanEventIsAttached() {
        Incident incident = incident("SECURITY", "FIREWALL_DENY", Severity.WARNING, "BANK-FW-01 security alert");
        Event netflow = new Event(
                "netflow-source-demo",
                "Demo NetFlow Collector",
                DeviceType.FIREWALL,
                "lab",
                "NETFLOW_PORT_SCAN",
                Severity.CRITICAL,
                "NetFlow detected repeated TCP connections",
                "sourceAddress=10.0.0.10; destinationAddress=10.10.10.20; anomalyType=PORT_SCAN",
                "raw",
                "10.0.0.10",
                null,
                null,
                "NETFLOW",
                Instant.now()
        );
        incident.addEvent(netflow);
        incident.markActive(DeviceStatus.WARNING, "BANK-FW-01 security alert");
        assertThat(policy.shouldCapture(incident)).isTrue();
    }

    @Test
    void genericTitleIsEligibleWhenAttachedFlowHasPortScanAnomaly() throws Exception {
        NetworkFlow flow = mock(NetworkFlow.class);
        when(flow.getAnomalyType()).thenReturn(NetFlowAnomalyType.PORT_SCAN);
        NetworkFlowRepository repository = mock(NetworkFlowRepository.class);
        when(repository.findByIncidentIdOrderByStartTimeDesc(42L)).thenReturn(List.of(flow));
        AutoCapturePolicy policyWithFlows = new AutoCapturePolicy(new PcapCaptureProperties(), repository);

        Incident incident = incident("SECURITY", "FIREWALL_DENY", Severity.WARNING, "security alert");
        var id = Incident.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(incident, 42L);

        assertThat(policyWithFlows.shouldCapture(incident)).isTrue();
    }

    @Test
    void structuredAnomalyTypeMakesGenericTitleEligible() {
        Incident incident = incident("SECURITY", "FIREWALL_DENY", Severity.WARNING, "security alert");
        Event event = new Event(
                "fw-1",
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "FIREWALL_DENY",
                Severity.WARNING,
                "security alert",
                "sourceAddress=10.0.0.10; destinationAddress=10.10.10.20; anomalyType=PORT_SCAN",
                "raw",
                "10.0.0.10",
                "BANK-FW-01",
                "FIREWALL",
                "SYSLOG",
                Instant.now()
        );
        incident.addEvent(event);
        assertThat(policy.shouldCapture(incident)).isTrue();
    }

    @Test
    void disabledAutoCaptureNeverMatches() {
        PcapCaptureProperties properties = new PcapCaptureProperties();
        properties.getAuto().setEnabled(false);
        AutoCapturePolicy disabled = new AutoCapturePolicy(properties);
        Incident incident = incident("SECURITY", "POSSIBLE_PORT_SCAN", Severity.CRITICAL, "possible port scan detected");
        assertThat(disabled.shouldCapture(incident)).isFalse();
    }

    private Incident incident(String category, String eventType, Severity severity, String title) {
        Event event = new Event(
                "fw-1",
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                eventType,
                severity,
                title,
                "sourceAddress=10.0.0.10; destinationAddress=10.10.10.20",
                "raw",
                "10.0.0.10",
                "BANK-FW-01",
                "FIREWALL",
                "SYSLOG",
                Instant.now()
        );
        return new Incident("fw-1:" + category, category, event, DeviceStatus.WARNING, "BANK-FW-01 " + title);
    }
}
