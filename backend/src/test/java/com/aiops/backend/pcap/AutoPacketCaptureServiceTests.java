package com.aiops.backend.pcap;

import com.aiops.backend.ai.AiContextBuilderService;
import com.aiops.backend.ai.AiIncidentContextResponse;
import com.aiops.backend.component.Criticality;
import com.aiops.backend.component.MonitoredComponentService;
import com.aiops.backend.component.MonitoringMethod;
import com.aiops.backend.component.NetworkInterfaceRole;
import com.aiops.backend.component.SaveComponentNetworkInterfaceRequest;
import com.aiops.backend.component.SaveMonitoredComponentRequest;
import com.aiops.backend.component.ComponentNetworkInterfaceService;
import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventIngestionRequest;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.EventService;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.incident.IncidentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "app.pcap.auto.enabled=true",
        "app.pcap.capture.async=false",
        "app.pcap.rolling.enabled=true"
})
class AutoPacketCaptureServiceTests {

    @Autowired
    private AutoPacketCaptureService autoPacketCaptureService;

    @Autowired
    private AutoCapturePolicy autoCapturePolicy;

    @Autowired
    private PacketCaptureJobService jobService;

    @Autowired
    private PacketCaptureJobRepository jobRepository;

    @Autowired
    private PacketCaptureAnalysisService analysisService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private MonitoredComponentService componentService;

    @Autowired
    private ComponentNetworkInterfaceService interfaceService;

    @Autowired
    private AiContextBuilderService aiContextBuilderService;

    @MockitoBean
    private PacketCaptureProvider captureProvider;

    @MockitoBean
    private PcapAnalysisClient analysisClient;

    @BeforeEach
    void stubProvider() {
        lenient().when(captureProvider.providerId()).thenReturn("LAB_SENSOR");
        if (componentService.list().stream().noneMatch(item -> "BANK-SRV-01".equals(item.name()))) {
            bankServerWithServiceLan();
        }
    }

    @Test
    void eligiblePortScanTriggersAutoRollingCapture() {
        Incident incident = savePortScanIncident();
        stubSuccessfulCapture();

        autoPacketCaptureService.captureIfEligible(incident.getId());

        ArgumentCaptor<ProviderCaptureRequest> captor = ArgumentCaptor.forClass(ProviderCaptureRequest.class);
        verify(captureProvider).startCapture(captor.capture());
        assertThat(captor.getValue().rollingSnapshot()).isTrue();
        assertThat(captor.getValue().sourceIp()).isEqualTo("10.0.0.10");
        assertThat(captor.getValue().destinationIp()).isEqualTo("10.10.10.20");
        List<PacketCaptureJob> jobs = jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId());
        assertThat(jobs).hasSize(1);
        assertThat(jobs.getFirst().getTrigger()).isEqualTo(CaptureTrigger.AUTO_ROLLING);
        assertThat(jobs.getFirst().getStatus()).isEqualTo(PacketCaptureJobStatus.COMPLETED);
        assertThat(jobs.getFirst().getPreTriggerSeconds()).isEqualTo(60);
        assertThat(jobs.getFirst().getPostTriggerSeconds()).isEqualTo(20);
    }

    @Test
    void additionalSyslogEventDoesNotStartSecondAutoCapture() {
        String deviceId = "fw-auto-" + UUID.randomUUID().toString().substring(0, 8);
        stubSuccessfulCapture();
        eventService.ingest(portScanIngestion(deviceId, Instant.now()));
        Incident incident = incidentRepository.findAll().stream()
                .filter(item -> deviceId.equals(item.getDeviceId()))
                .findFirst()
                .orElseThrow();
        autoPacketCaptureService.captureIfEligible(incident.getId());

        eventService.ingest(portScanIngestion(deviceId, Instant.now().plusSeconds(3)));
        autoPacketCaptureService.captureIfEligible(incident.getId());

        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getEventCount()).isGreaterThanOrEqualTo(2);
        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).hasSize(1);
        verify(captureProvider, times(1)).startCapture(any());
    }

    @Test
    void resolvedIncidentDoesNotAutoCapture() {
        Incident incident = savePortScanIncident();
        incident.resolve();
        incidentRepository.save(incident);

        autoPacketCaptureService.captureIfEligible(incident.getId());

        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).isEmpty();
        assertThat(incidentRepository.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    void availabilityIncidentDoesNotAutoCapture() {
        Event event = eventRepository.save(new Event(
                "cam-" + UUID.randomUUID(),
                "BANK-CAMERA-01",
                DeviceType.IP_CAMERA,
                "lab",
                "RTSP_UNREACHABLE",
                Severity.CRITICAL,
                "stream down",
                "destinationAddress=10.10.10.40",
                "raw",
                null,
                "camera",
                "CAMERA",
                "SYSLOG",
                Instant.now()
        ));
        Incident incident = incidentRepository.save(new Incident(
                event.getDeviceId() + ":VIDEO_STREAM",
                "VIDEO_STREAM",
                event,
                DeviceStatus.DOWN,
                "BANK-CAMERA-01 video stream problem"
        ));

        autoPacketCaptureService.captureIfEligible(incident.getId());

        assertThat(autoCapturePolicy.shouldCapture(incident)).isFalse();
        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).isEmpty();
    }

    @Test
    void providerUnavailableDoesNotBreakIncidentAndRecordsFailedJob() {
        Incident incident = savePortScanIncident();
        when(captureProvider.startCapture(any())).thenThrow(new CaptureProviderException(
                PacketCaptureFailureCode.PROVIDER_UNAVAILABLE,
                "Capture sensor is unavailable"
        ));

        assertThatCode(() -> autoPacketCaptureService.captureIfEligible(incident.getId()))
                .doesNotThrowAnyException();

        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IncidentStatus.ACTIVE);
        PacketCaptureJob job = jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId()).getFirst();
        assertThat(job.getTrigger()).isEqualTo(CaptureTrigger.AUTO_ROLLING);
        assertThat(job.getStatus()).isEqualTo(PacketCaptureJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(PacketCaptureFailureCode.PROVIDER_UNAVAILABLE);
    }

    @Test
    void duplicateAutoRequestIsIdempotent() {
        Incident incident = savePortScanIncident();
        stubSuccessfulCapture();

        autoPacketCaptureService.captureIfEligible(incident.getId());
        autoPacketCaptureService.captureIfEligible(incident.getId());

        assertThat(jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).hasSize(1);
        verify(captureProvider, times(1)).startCapture(any());
    }

    @Test
    void manualCaptureStillWorksAndIsDistinguishableFromAuto() {
        Incident incident = savePortScanIncident();
        stubSuccessfulCapture();
        autoPacketCaptureService.captureIfEligible(incident.getId());

        PacketCaptureJobResponse manual = jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null));

        assertThat(manual.trigger()).isEqualTo(CaptureTrigger.MANUAL);
        List<PacketCaptureJob> jobs = jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId());
        assertThat(jobs).extracting(PacketCaptureJob::getTrigger)
                .containsExactlyInAnyOrder(CaptureTrigger.MANUAL, CaptureTrigger.AUTO_ROLLING);
    }

    @Test
    void completedAutoPcapGoesThroughTsharkAndAiContext() {
        Incident incident = savePortScanIncident();
        byte[] captured = pcapBytes();
        stubSuccessfulCapture(captured);

        autoPacketCaptureService.captureIfEligible(incident.getId());

        verify(analysisClient).analyze(anyString(), any(), org.mockito.ArgumentMatchers.eq(captured));
        PacketCaptureJob job = jobRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId()).getFirst();
        assertThat(analysisService.listForIncident(incident.getId()))
                .anyMatch(item -> item.id().equals(job.getAnalysisId()));
        AiIncidentContextResponse context = aiContextBuilderService.build(incident.getId());
        assertThat(context.packetCaptureSummaries())
                .anyMatch(item -> item.id().equals(job.getAnalysisId()));
    }

    @Test
    void activeJobRuleStillAppliesToManualStart() {
        Incident incident = savePortScanIncident();
        PacketCaptureJob running = new PacketCaptureJob(
                incident.getId(),
                "LAB_SENSOR",
                "10.0.0.10",
                "10.10.10.20",
                "bank-firewall-wan",
                "BANK-FW-01 / WAN",
                "eth1",
                20,
                CaptureTrigger.AUTO_ROLLING,
                20,
                20
        );
        running.markRunning("cap-running");
        jobRepository.save(running);

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null))
        );
    }

    private void stubSuccessfulCapture() {
        stubSuccessfulCapture(pcapBytes());
    }

    private void stubSuccessfulCapture(byte[] bytes) {
        when(captureProvider.startCapture(any())).thenReturn(
                new ProviderCaptureHandle("cap-auto", PacketCaptureJobStatus.RUNNING)
        );
        when(captureProvider.getCaptureStatus("cap-auto")).thenReturn(new ProviderCaptureStatus(
                "cap-auto",
                PacketCaptureJobStatus.COMPLETED,
                7,
                (long) bytes.length,
                null,
                null
        ));
        when(captureProvider.retrieveCapture("cap-auto")).thenReturn(bytes);
        when(analysisClient.analyze(any(), any(), any())).thenReturn(analysisResponse(7, bytes.length));
    }

    private FastApiPacketCaptureAnalysisResponse analysisResponse(int packets, long bytes) {
        return new FastApiPacketCaptureAnalysisResponse(
                packets,
                bytes,
                List.of("10.0.0.10 (" + packets + ")"),
                List.of("10.10.10.20 (" + packets + ")"),
                List.of("TCP (" + packets + ")"),
                List.of("21 (1)", "22 (1)", "23 (1)", "80 (1)", "443 (1)", "3306 (1)", "5432 (1)"),
                List.of("Possible port scan or reconnaissance from 10.0.0.10 to 10.10.10.20"),
                "Analyzed " + packets + " packets."
        );
    }

    private byte[] pcapBytes() {
        return new byte[]{(byte) 0xd4, (byte) 0xc3, (byte) 0xb2, (byte) 0xa1, 1, 2, 3, 4};
    }

    private void bankServerWithServiceLan() {
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

    private Incident savePortScanIncident() {
        Event event = eventRepository.save(portScanEvent("fw-auto-" + UUID.randomUUID().toString().substring(0, 8), Instant.now()));
        return incidentRepository.save(new Incident(
                event.getDeviceId() + ":SECURITY:" + UUID.randomUUID(),
                "SECURITY",
                event,
                DeviceStatus.WARNING,
                "BANK-FW-01 possible port scan detected"
        ));
    }

    private EventIngestionRequest portScanIngestion(String deviceId, Instant occurredAt) {
        return new EventIngestionRequest(
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
        );
    }

    private Event portScanEvent(String deviceId, Instant occurredAt) {
        return new Event(
                deviceId,
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "POSSIBLE_PORT_SCAN",
                Severity.CRITICAL,
                "Possible port scan",
                "sourceAddress=10.0.0.10; destinationAddress=10.10.10.20; destinationPort=22; anomalyType=PORT_SCAN",
                "<134> DENY TCP 10.0.0.10:1 -> 10.10.10.20:22",
                "10.0.0.10",
                "BANK-FW-01",
                "FIREWALL",
                "SYSLOG",
                occurredAt
        );
    }
}
