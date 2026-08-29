package com.aiops.backend.pcap;

import com.aiops.backend.component.Criticality;
import com.aiops.backend.component.MonitoredComponentService;
import com.aiops.backend.component.MonitoringMethod;
import com.aiops.backend.component.NetworkInterfaceRole;
import com.aiops.backend.component.SaveComponentNetworkInterfaceRequest;
import com.aiops.backend.component.SaveMonitoredComponentRequest;
import com.aiops.backend.component.ComponentNetworkInterfaceService;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.EventIngestionRequest;
import com.aiops.backend.event.EventService;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "app.pcap.auto.enabled=true",
        "app.pcap.capture.async=false",
        "app.pcap.rolling.enabled=true"
})
class IncidentCreatedAutoCaptureTests {

    @Autowired
    private EventService eventService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private PacketCaptureJobRepository jobRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MonitoredComponentService componentService;

    @Autowired
    private ComponentNetworkInterfaceService interfaceService;

    @MockitoBean
    private PacketCaptureProvider captureProvider;

    @MockitoBean
    private PcapAnalysisClient analysisClient;

    @BeforeEach
    void stubProvider() {
        lenient().when(captureProvider.providerId()).thenReturn("LAB_SENSOR");
        byte[] bytes = new byte[]{(byte) 0xd4, (byte) 0xc3, (byte) 0xb2, (byte) 0xa1, 1, 2, 3, 4};
        when(captureProvider.startCapture(any())).thenReturn(
                new ProviderCaptureHandle("cap-evt", PacketCaptureJobStatus.RUNNING)
        );
        when(captureProvider.getCaptureStatus("cap-evt")).thenReturn(new ProviderCaptureStatus(
                "cap-evt",
                PacketCaptureJobStatus.COMPLETED,
                7,
                (long) bytes.length,
                null,
                null
        ));
        when(captureProvider.retrieveCapture("cap-evt")).thenReturn(bytes);
        when(analysisClient.analyze(any(), any(), any())).thenReturn(new FastApiPacketCaptureAnalysisResponse(
                7,
                bytes.length,
                List.of("10.0.0.10 (7)"),
                List.of("10.10.10.20 (7)"),
                List.of("TCP (7)"),
                List.of("22 (7)"),
                List.of("Possible port scan"),
                "ok"
        ));
        if (componentService.list().stream().noneMatch(item -> "BANK-SRV-01".equals(item.name()))) {
            var server = componentService.create(new SaveMonitoredComponentRequest(
                    "BANK-SRV-01",
                    DeviceType.SERVER,
                    "172.30.30.20",
                    null,
                    22,
                    161,
                    "public",
                    "1.3.6.1.2.1.1.1.0",
                    "lab",
                    Criticality.HIGH,
                    Set.of(MonitoringMethod.PING),
                    30,
                    true
            ));
            interfaceService.create(server.id(), new SaveComponentNetworkInterfaceRequest(
                    "Service LAN",
                    "10.10.10.20",
                    NetworkInterfaceRole.SERVICE,
                    false
            ));
        }
    }

    @Test
    void listenerCapturesAfterIncidentIsCommitted() {
        String deviceId = "fw-listener-" + UUID.randomUUID().toString().substring(0, 8);
        transactionTemplate.executeWithoutResult(status -> ingest(deviceId, Instant.now()));

        Incident incident = latest(deviceId);
        List<PacketCaptureJob> jobs = jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId());
        assertThat(jobs).hasSize(1);
        assertThat(jobs.getFirst().getTrigger()).isEqualTo(CaptureTrigger.AUTO_ROLLING);
        assertThat(jobs.getFirst().getStatus()).isEqualTo(PacketCaptureJobStatus.COMPLETED);
        verify(captureProvider, times(1)).startCapture(any());
    }

    @Test
    void laterEventOnSameEpisodeDoesNotCreateSecondAutoJob() {
        String deviceId = "fw-listener2-" + UUID.randomUUID().toString().substring(0, 8);
        Instant t0 = Instant.now();
        transactionTemplate.executeWithoutResult(status -> ingest(deviceId, t0));
        transactionTemplate.executeWithoutResult(status -> ingest(deviceId, t0.plusSeconds(3)));

        Incident incident = latest(deviceId);
        assertThat(incident.getEventCount()).isGreaterThanOrEqualTo(2);
        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).hasSize(1);
        verify(captureProvider, times(1)).startCapture(any());
    }

    @Test
    void newEpisodeTriggersNewSnapshot() {
        String deviceId = "fw-listener3-" + UUID.randomUUID().toString().substring(0, 8);
        Instant t0 = Instant.now();
        transactionTemplate.executeWithoutResult(status -> ingest(deviceId, t0));
        Incident first = latest(deviceId);
        transactionTemplate.executeWithoutResult(status -> {
            Incident managed = incidentRepository.findById(first.getId()).orElseThrow();
            managed.resolve();
            incidentRepository.save(managed);
        });
        transactionTemplate.executeWithoutResult(status -> ingest(deviceId, t0.plusSeconds(5)));

        List<Incident> episodes = incidentRepository.findAll().stream()
                .filter(incident -> deviceId.equals(incident.getDeviceId()))
                .toList();
        assertThat(episodes).hasSize(2);
        for (Incident episode : episodes) {
            assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(episode.getId())).hasSize(1);
        }
        verify(captureProvider, times(2)).startCapture(any());
    }

    private void ingest(String deviceId, Instant occurredAt) {
        eventService.ingest(new EventIngestionRequest(
                deviceId,
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "POSSIBLE_PORT_SCAN",
                Severity.CRITICAL,
                "Possible port scan",
                "sourceAddress=10.0.0.10; destinationAddress=10.10.10.20; destinationPort=22; anomalyType=PORT_SCAN",
                "DENY TCP 10.0.0.10 -> 10.10.10.20:22",
                "10.0.0.10",
                "BANK-FW-01",
                "FIREWALL",
                "SYSLOG",
                occurredAt
        ));
    }

    private Incident latest(String deviceId) {
        return incidentRepository.findAll().stream()
                .filter(incident -> deviceId.equals(incident.getDeviceId()))
                .findFirst()
                .orElseThrow();
    }
}
