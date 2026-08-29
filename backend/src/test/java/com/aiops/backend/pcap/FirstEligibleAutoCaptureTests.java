package com.aiops.backend.pcap;

import com.aiops.backend.component.ComponentNetworkInterfaceService;
import com.aiops.backend.component.Criticality;
import com.aiops.backend.component.MonitoredComponentService;
import com.aiops.backend.component.MonitoringMethod;
import com.aiops.backend.component.NetworkInterfaceRole;
import com.aiops.backend.component.SaveComponentNetworkInterfaceRequest;
import com.aiops.backend.component.SaveMonitoredComponentRequest;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.EventIngestionRequest;
import com.aiops.backend.event.EventService;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.netflow.NetFlowAnomalyDetectionService;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "app.pcap.auto.enabled=true",
        "app.pcap.capture.async=false",
        "app.pcap.rolling.enabled=true"
})
class FirstEligibleAutoCaptureTests {

    @Autowired
    private EventService eventService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private PacketCaptureJobRepository jobRepository;

    @Autowired
    private PacketCaptureJobService jobService;

    @Autowired
    private NetFlowAnomalyDetectionService anomalyDetectionService;

    @Autowired
    private NetworkFlowRepository networkFlowRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private IncidentAutoCaptureCoordinator coordinator;

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
                new ProviderCaptureHandle("cap-first", PacketCaptureJobStatus.RUNNING)
        );
        when(captureProvider.getCaptureStatus("cap-first")).thenReturn(new ProviderCaptureStatus(
                "cap-first",
                PacketCaptureJobStatus.COMPLETED,
                7,
                (long) bytes.length,
                null,
                null
        ));
        when(captureProvider.retrieveCapture("cap-first")).thenReturn(bytes);
        when(analysisClient.analyze(any(), any(), any())).thenReturn(new FastApiPacketCaptureAnalysisResponse(
                7,
                bytes.length,
                List.of("10.0.0.10 (7)"),
                List.of("10.10.10.20 (7)"),
                List.of("TCP (7)"),
                List.of("21 (1)", "22 (1)", "23 (1)", "80 (1)", "443 (1)", "3306 (1)", "5432 (1)"),
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
    void eligibleAtCreationStartsAutoCaptureOnce() {
        Endpoints endpoints = uniqueEndpoints();
        String deviceId = "fw-eligible-" + UUID.randomUUID().toString().substring(0, 8);
        transactionTemplate.executeWithoutResult(status -> ingestPortScan(deviceId, Instant.now(), endpoints));

        Incident incident = latest(deviceId);
        List<PacketCaptureJob> jobs = jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId());
        assertThat(jobs).hasSize(1);
        assertThat(jobs.getFirst().getTrigger()).isEqualTo(CaptureTrigger.AUTO_ROLLING);
        verify(captureProvider, times(1)).startCapture(any());
    }

    @Test
    void genericDenyIncidentDoesNotAutoCapture() {
        Endpoints endpoints = uniqueEndpoints();
        String deviceId = "fw-deny-" + UUID.randomUUID().toString().substring(0, 8);
        transactionTemplate.executeWithoutResult(status -> ingestDeny(deviceId, Instant.now(), endpoints));

        Incident incident = latest(deviceId);
        assertThat(incident.getTitle().toLowerCase()).contains("security alert");
        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).isEmpty();
        verify(captureProvider, never()).startCapture(any());
    }

    @Test
    void netFlowPortScanOnSameDenyIncidentStartsAutoCaptureOnce() {
        Endpoints endpoints = uniqueEndpoints();
        String deviceId = "fw-deny-nf-" + UUID.randomUUID().toString().substring(0, 8);
        Instant t0 = Instant.now();
        transactionTemplate.executeWithoutResult(status -> ingestDeny(deviceId, t0, endpoints));
        Incident incident = latest(deviceId);
        Instant activityBefore = incident.getLastActivityAt();
        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).isEmpty();

        List<NetworkFlow> scan = savePortScan(endpoints.source(), endpoints.destination(), t0.plusSeconds(2), 21, 22, 23, 80, 443, 3306, 5432);
        transactionTemplate.executeWithoutResult(status -> anomalyDetectionService.analyze(null, scan));

        Incident enriched = incidentRepository.findWithEventsById(incident.getId()).orElseThrow();
        assertThat(scan.stream().map(flow -> networkFlowRepository.findById(flow.getId()).orElseThrow().getIncidentId()))
                .containsOnly(incident.getId());
        List<PacketCaptureJob> jobs = jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId());
        assertThat(jobs).hasSize(1);
        assertThat(jobs.getFirst().getTrigger()).isEqualTo(CaptureTrigger.AUTO_ROLLING);
        assertThat(jobs.getFirst().getStatus()).isEqualTo(PacketCaptureJobStatus.COMPLETED);
        verify(captureProvider, times(1)).startCapture(any());
        assertThat(enriched.getLastActivityAt()).isNotNull();
        assertThat(activityBefore).isNotNull();
    }

    @Test
    void sevenSuspiciousFlowsCreateOnlyOneAutoJob() {
        Endpoints endpoints = uniqueEndpoints();
        String deviceId = "fw-deny-7-" + UUID.randomUUID().toString().substring(0, 8);
        Instant t0 = Instant.now();
        transactionTemplate.executeWithoutResult(status -> ingestDeny(deviceId, t0, endpoints));
        Incident incident = latest(deviceId);

        List<NetworkFlow> scan = savePortScan(endpoints.source(), endpoints.destination(), t0.plusSeconds(1), 21, 22, 23, 80, 443, 3306, 5432);
        transactionTemplate.executeWithoutResult(status -> anomalyDetectionService.analyze(null, scan));
        transactionTemplate.executeWithoutResult(status -> anomalyDetectionService.analyze(null, scan));

        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).hasSize(1);
        verify(captureProvider, times(1)).startCapture(any());
    }

    @Test
    void laterSyslogOnEnrichedIncidentDoesNotDuplicateAutoCapture() {
        Endpoints endpoints = uniqueEndpoints();
        String deviceId = "fw-deny-sys-" + UUID.randomUUID().toString().substring(0, 8);
        Instant t0 = Instant.now();
        transactionTemplate.executeWithoutResult(status -> ingestDeny(deviceId, t0, endpoints));
        Incident incident = latest(deviceId);
        List<NetworkFlow> scan = savePortScan(endpoints.source(), endpoints.destination(), t0.plusSeconds(1), 21, 22, 23, 80, 443, 3306, 5432);
        transactionTemplate.executeWithoutResult(status -> anomalyDetectionService.analyze(null, scan));
        transactionTemplate.executeWithoutResult(status -> ingestDeny(deviceId, t0.plusSeconds(8), endpoints));

        assertThat(incidentRepository.findWithEventsById(incident.getId()).orElseThrow().getEventCount())
                .isGreaterThanOrEqualTo(2);
        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).hasSize(1);
        verify(captureProvider, times(1)).startCapture(any());
    }

    @Test
    void providerFailureOnLaterEligibilityDoesNotBreakNetFlowAttach() {
        Endpoints endpoints = uniqueEndpoints();
        String deviceId = "fw-deny-fail-" + UUID.randomUUID().toString().substring(0, 8);
        Instant t0 = Instant.now();
        transactionTemplate.executeWithoutResult(status -> ingestDeny(deviceId, t0, endpoints));
        Incident incident = latest(deviceId);
        when(captureProvider.startCapture(any())).thenThrow(new CaptureProviderException(
                PacketCaptureFailureCode.PROVIDER_UNAVAILABLE,
                "Capture sensor is unavailable"
        ));

        List<NetworkFlow> scan = savePortScan(endpoints.source(), endpoints.destination(), t0.plusSeconds(1), 21, 22, 23, 80, 443);
        assertThatCode(() -> transactionTemplate.executeWithoutResult(
                status -> anomalyDetectionService.analyze(null, scan)
        )).doesNotThrowAnyException();

        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getStatus().isOpen()).isTrue();
        assertThat(networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(incident.getId())).isNotEmpty();
        PacketCaptureJob job = jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId()).getFirst();
        assertThat(job.getStatus()).isEqualTo(PacketCaptureJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(PacketCaptureFailureCode.PROVIDER_UNAVAILABLE);
    }

    @Test
    void resolvedIncidentDoesNotAutoCapture() {
        Endpoints endpoints = uniqueEndpoints();
        String deviceId = "fw-deny-res-" + UUID.randomUUID().toString().substring(0, 8);
        transactionTemplate.executeWithoutResult(status -> ingestDeny(deviceId, Instant.now(), endpoints));
        Incident incident = latest(deviceId);
        transactionTemplate.executeWithoutResult(status -> {
            Incident managed = incidentRepository.findById(incident.getId()).orElseThrow();
            managed.resolve();
            incidentRepository.save(managed);
        });

        coordinator.evaluateAndTrigger(incident.getId(), "after-resolve");

        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).isEmpty();
        verify(captureProvider, never()).startCapture(any());
    }

    @Test
    void nonSecurityIncidentDoesNotAutoCapture() {
        String deviceId = "srv-avail-" + UUID.randomUUID().toString().substring(0, 8);
        transactionTemplate.executeWithoutResult(status -> eventService.ingest(new EventIngestionRequest(
                deviceId,
                "BANK-SRV-01",
                DeviceType.SERVER,
                "lab",
                "PING_UNREACHABLE",
                Severity.CRITICAL,
                "host down",
                "destinationAddress=10.10.10.20",
                "raw",
                null,
                "BANK-SRV-01",
                "SERVER",
                "SYSLOG",
                Instant.now()
        )));

        Incident incident = latest(deviceId);
        assertThat(incident.getCategory()).isNotEqualTo("SECURITY");
        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).isEmpty();
        verify(captureProvider, never()).startCapture(any());
    }

    @Test
    void coordinatorEvaluationDoesNotChangeActivityTimestamps() {
        Endpoints endpoints = uniqueEndpoints();
        String deviceId = "fw-deny-ts-" + UUID.randomUUID().toString().substring(0, 8);
        transactionTemplate.executeWithoutResult(status -> ingestDeny(deviceId, Instant.now(), endpoints));
        Incident incident = latest(deviceId);
        Instant lastActivity = incident.getLastActivityAt();
        Instant lastSeen = incident.getLastSeenAt();

        coordinator.evaluateAndTrigger(incident.getId(), "test-no-mutation");

        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getLastActivityAt()).isEqualTo(lastActivity);
        assertThat(reloaded.getLastSeenAt()).isEqualTo(lastSeen);
        assertThat(reloaded.getStatus()).isEqualTo(incident.getStatus());
        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).isEmpty();
    }

    @Test
    void manualCaptureStillWorksAfterAutoRolling() {
        Endpoints endpoints = uniqueEndpoints();
        String deviceId = "fw-deny-man-" + UUID.randomUUID().toString().substring(0, 8);
        Instant t0 = Instant.now();
        transactionTemplate.executeWithoutResult(status -> ingestDeny(deviceId, t0, endpoints));
        Incident incident = latest(deviceId);
        List<NetworkFlow> scan = savePortScan(endpoints.source(), endpoints.destination(), t0.plusSeconds(1), 21, 22, 23, 80, 443);
        transactionTemplate.executeWithoutResult(status -> anomalyDetectionService.analyze(null, scan));

        PacketCaptureJobResponse manual = jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null));
        assertThat(manual.trigger()).isEqualTo(CaptureTrigger.MANUAL);
        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId()))
                .extracting(PacketCaptureJob::getTrigger)
                .containsExactlyInAnyOrder(CaptureTrigger.MANUAL, CaptureTrigger.AUTO_ROLLING);
    }

    private void ingestPortScan(String deviceId, Instant occurredAt, Endpoints endpoints) {
        eventService.ingest(new EventIngestionRequest(
                deviceId,
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "POSSIBLE_PORT_SCAN",
                Severity.CRITICAL,
                "Possible port scan",
                "sourceAddress=" + endpoints.source() + "; destinationAddress=" + endpoints.destination()
                        + "; destinationPort=22; anomalyType=PORT_SCAN",
                "DENY TCP " + endpoints.source() + " -> " + endpoints.destination() + ":22",
                endpoints.source(),
                "BANK-FW-01",
                "FIREWALL",
                "SYSLOG",
                occurredAt
        ));
    }

    private void ingestDeny(String deviceId, Instant occurredAt, Endpoints endpoints) {
        eventService.ingest(new EventIngestionRequest(
                deviceId,
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "FIREWALL_DENY",
                Severity.WARNING,
                "DENY SRC=" + endpoints.source() + " DST=" + endpoints.destination() + " SPT=40000 DPT=22 PROTO=TCP",
                "sourceAddress=" + endpoints.source() + "; destinationAddress=" + endpoints.destination() + "; destinationPort=22",
                "<134> BANK-FW-01 firewall: DENY SRC=" + endpoints.source() + " DST=" + endpoints.destination() + " DPT=22",
                endpoints.source(),
                "BANK-FW-01",
                "FIREWALL",
                "SYSLOG",
                occurredAt
        ));
    }

    private record Endpoints(String source, String destination) {
    }

    private Endpoints uniqueEndpoints() {
        int host = 40 + Math.floorMod(UUID.randomUUID().hashCode(), 200);
        return new Endpoints("10.0.8." + host, "10.10.8." + host);
    }

    private Incident latest(String deviceId) {
        return incidentRepository.findAll().stream()
                .filter(incident -> deviceId.equals(incident.getDeviceId()))
                .findFirst()
                .orElseThrow();
    }

    private List<NetworkFlow> savePortScan(String sourceIp, String destinationIp, Instant start, int... ports) {
        List<NetworkFlow> flows = new ArrayList<>();
        for (int index = 0; index < ports.length; index++) {
            Instant flowStart = start.plusSeconds(index);
            NetworkFlow flow = new NetworkFlow(
                    null,
                    flowStart,
                    flowStart.plusSeconds(1),
                    1000,
                    sourceIp,
                    destinationIp,
                    40000 + index,
                    ports[index],
                    "TCP",
                    4,
                    256,
                    "test-exporter",
                    1,
                    2,
                    UUID.randomUUID().toString().replace("-", ""),
                    sourceIp + " -> " + destinationIp + ":" + ports[index]
            );
            flows.add(networkFlowRepository.save(flow));
        }
        return flows;
    }
}
