package com.aiops.backend.pcap;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.ai.AiContextBuilderService;
import com.aiops.backend.ai.AiIncidentContextResponse;
import com.aiops.backend.component.Criticality;
import com.aiops.backend.component.MonitoredComponentService;
import com.aiops.backend.component.MonitoringMethod;
import com.aiops.backend.component.NetworkInterfaceRole;
import com.aiops.backend.component.SaveComponentNetworkInterfaceRequest;
import com.aiops.backend.component.SaveMonitoredComponentRequest;
import com.aiops.backend.component.ComponentNetworkInterfaceService;
import com.aiops.backend.component.MonitoredComponentResponse;
import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.incident.IncidentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PacketCaptureJobServiceTests {

    @Autowired
    private PacketCaptureJobService jobService;

    @Autowired
    private PacketCaptureAnalysisService analysisService;

    @Autowired
    private PacketCaptureAnalysisRepository analysisRepository;

    @Autowired
    private PacketCaptureJobRepository jobRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private MonitoredComponentService componentService;

    @Autowired
    private ComponentNetworkInterfaceService interfaceService;

    @Autowired
    private AiContextBuilderService aiContextBuilderService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PacketCaptureProvider captureProvider;

    @MockitoBean
    private PcapAnalysisClient analysisClient;

    @BeforeEach
    void stubProviderIdentity() {
        lenient().when(captureProvider.providerId()).thenReturn("LAB_SENSOR");
    }

    @Test
    void captureRequestDerivesSourceAndDestinationFromIncident() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();
        stubSuccessfulCapture();

        PacketCaptureJobResponse job = jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null));

        ArgumentCaptor<ProviderCaptureRequest> captor = ArgumentCaptor.forClass(ProviderCaptureRequest.class);
        verify(captureProvider).startCapture(captor.capture());
        assertThat(captor.getValue().sourceIp()).isEqualTo("10.0.0.10");
        assertThat(captor.getValue().destinationIp()).isEqualTo("10.10.10.20");
        assertThat(captor.getValue().interfaceName()).isEqualTo("eth1");
        assertThat(job.sourceIp()).isEqualTo("10.0.0.10");
        assertThat(job.destinationIp()).isEqualTo("10.10.10.20");
        assertThat(job.trigger()).isEqualTo(CaptureTrigger.MANUAL);
    }

    @Test
    void arbitraryBpfCannotBeSubmitted() throws Exception {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();
        stubSuccessfulCapture();

        mockMvc.perform(post("/api/incidents/" + incident.getId() + "/packet-captures/start")
                        .header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "durationSeconds": 20,
                                  "bpf": "host 1.2.3.4; id",
                                  "tcpdumpCommand": "tcpdump -i eth1; rm -rf /"
                                }
                                """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<ProviderCaptureRequest> captor = ArgumentCaptor.forClass(ProviderCaptureRequest.class);
        verify(captureProvider).startCapture(captor.capture());
        assertThat(captor.getValue().sourceIp()).isEqualTo("10.0.0.10");
        assertThat(captor.getValue().destinationIp()).isEqualTo("10.10.10.20");
        assertThat(captor.getValue().toString()).doesNotContain("rm -rf");
        assertThat(captor.getValue().toString()).doesNotContain("1.2.3.4");
    }

    @Test
    void durationAboveMaxIsClamped() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();
        stubSuccessfulCapture();

        PacketCaptureJobResponse job = jobService.start(incident.getId(), new StartPacketCaptureRequest(120, null));

        ArgumentCaptor<ProviderCaptureRequest> captor = ArgumentCaptor.forClass(ProviderCaptureRequest.class);
        verify(captureProvider).startCapture(captor.capture());
        assertThat(captor.getValue().durationSeconds()).isEqualTo(60);
        assertThat(job.durationSeconds()).isEqualTo(60);
    }

    @Test
    void resolvedIncidentCannotStartLiveCapture() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();
        incident.resolve();
        incidentRepository.save(incident);

        assertThatThrownBy(() -> jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException status = (ResponseStatusException) exception;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(status.getReason()).contains("resolved");
                });
        PacketCapturePreviewResponse preview = jobService.preview(incident.getId());
        assertThat(preview.liveCaptureAllowed()).isFalse();
        assertThat(incidentRepository.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    void providerUnavailableReturnsMeaningfulError() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();
        when(captureProvider.startCapture(any())).thenThrow(new CaptureProviderException(
                PacketCaptureFailureCode.PROVIDER_UNAVAILABLE,
                "Capture sensor is unavailable"
        ));

        assertThatThrownBy(() -> jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException status = (ResponseStatusException) exception;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(status.getReason()).contains("unavailable");
                });
        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IncidentStatus.ACTIVE);
        assertThat(analysisRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).isEmpty();
    }

    @Test
    void successfulCaptureBecomesAttachedPcapEvidence() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();
        stubSuccessfulCapture();

        PacketCaptureJobResponse job = jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null));

        assertThat(job.status()).isEqualTo(PacketCaptureJobStatus.COMPLETED);
        assertThat(job.packetCount()).isEqualTo(23);
        assertThat(job.fileSizeBytes()).isEqualTo(8L);
        assertThat(job.analysisId()).isNotNull();
        assertThat(analysisService.listForIncident(incident.getId()))
                .anyMatch(item -> item.id().equals(job.analysisId()) && item.totalPackets() == 23);
        assertThat(incidentRepository.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.ACTIVE);
    }

    @Test
    void existingManualUploadPathStillWorks() {
        Incident incident = savePortScanIncident();
        when(analysisClient.analyze(any(), any(), any())).thenReturn(analysisResponse(12, 1800));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.pcap",
                "application/vnd.tcpdump.pcap",
                pcapBytes()
        );

        PacketCaptureAnalysisResponse uploaded = analysisService.upload(incident.getId(), file);

        verify(analysisClient).analyze("manual.pcap", "application/vnd.tcpdump.pcap", pcapBytes());
        assertThat(uploaded.fileName()).isEqualTo("manual.pcap");
        assertThat(uploaded.fileSize()).isEqualTo(pcapBytes().length);
        assertThat(uploaded.totalBytes()).isEqualTo(1800);
        assertThat(uploaded.capturedPacketBytes()).isEqualTo(1800);
        assertThat(uploaded.fileSize()).isNotEqualTo(uploaded.totalBytes());
        assertThat(analysisService.listForIncident(incident.getId())).extracting(PacketCaptureAnalysisResponse::fileName)
                .contains("manual.pcap");
    }

    @Test
    void pcapFileSizeAndTsharkCapturedBytesCanDiffer() throws Exception {
        Incident incident = savePortScanIncident();
        byte[] pcap = new byte[654];
        java.util.Arrays.fill(pcap, (byte) 0x11);
        pcap[0] = (byte) 0xd4;
        pcap[1] = (byte) 0xc3;
        pcap[2] = (byte) 0xb2;
        pcap[3] = (byte) 0xa1;
        when(analysisClient.analyze(any(), any(), any())).thenReturn(analysisResponse(7, 518));

        PacketCaptureAnalysisResponse saved = analysisService.ingest(
                incident.getId(),
                "scan.pcap",
                "application/vnd.tcpdump.pcap",
                pcap,
                AuditAction.PACKET_CAPTURE_UPLOADED
        );

        assertThat(saved.totalPackets()).isEqualTo(7);
        assertThat(saved.fileSize()).isEqualTo(654L);
        assertThat(saved.totalBytes()).isEqualTo(518L);
        assertThat(saved.capturedPacketBytes()).isEqualTo(518L);
        assertThat(saved.fileSize()).isNotEqualTo(saved.capturedPacketBytes());

        mockMvc.perform(get("/api/incidents/" + incident.getId() + "/pcap")
                        .header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileSize").value(654))
                .andExpect(jsonPath("$[0].totalBytes").value(518))
                .andExpect(jsonPath("$[0].capturedPacketBytes").value(518))
                .andExpect(jsonPath("$[0].totalPackets").value(7));
    }

    @Test
    void liveCaptureStoresFileSizeFromRetrievedPcapNotTsharkBytes() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();
        byte[] pcap = new byte[654];
        java.util.Arrays.fill(pcap, (byte) 0x22);
        pcap[0] = (byte) 0xd4;
        pcap[1] = (byte) 0xc3;
        pcap[2] = (byte) 0xb2;
        pcap[3] = (byte) 0xa1;
        stubSuccessfulCapture(pcap);
        when(analysisClient.analyze(any(), any(), any())).thenReturn(analysisResponse(7, 518));

        PacketCaptureJobResponse job = jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null));

        assertThat(job.packetCount()).isEqualTo(7);
        assertThat(job.fileSizeBytes()).isEqualTo(654L);
        PacketCaptureAnalysisResponse analysis = analysisService.listForIncident(incident.getId()).getFirst();
        assertThat(analysis.fileSize()).isEqualTo(654L);
        assertThat(analysis.capturedPacketBytes()).isEqualTo(518L);
        assertThat(analysis.totalBytes()).isEqualTo(518L);
    }

    @Test
    void automatedCaptureCallsExistingTsharkAnalysis() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();
        byte[] captured = pcapBytes();
        stubSuccessfulCapture(captured);

        jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null));

        verify(analysisClient).analyze(anyString(), any(), org.mockito.ArgumentMatchers.eq(captured));
    }

    @Test
    void completedPcapAppearsInAiContext() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();
        stubSuccessfulCapture();

        PacketCaptureJobResponse job = jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null));
        AiIncidentContextResponse context = aiContextBuilderService.build(incident.getId());

        assertThat(job.analysisId()).isNotNull();
        assertThat(context.packetCaptureSummaries()).isNotEmpty();
        assertThat(context.packetCaptureSummaries())
                .anyMatch(item -> item.id().equals(job.analysisId()));
    }

    @Test
    void oneActiveCapturePerIncident() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();
        PacketCaptureJob running = new PacketCaptureJob(
                incident.getId(),
                "LAB_SENSOR",
                "10.0.0.10",
                "10.10.10.20",
                "bank-firewall-wan",
                "BANK-FW-01 / WAN",
                "eth1",
                20
        );
        running.markRunning("cap-running");
        jobRepository.save(running);

        assertThatThrownBy(() -> jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception ->
                        assertThat(((ResponseStatusException) exception).getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
                );
    }

    @Test
    void externalSourceRemainsExternalAndDestinationResolvesBankServer() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();

        PacketCapturePreviewResponse preview = jobService.preview(incident.getId());

        assertThat(preview.sourceIp()).isEqualTo("10.0.0.10");
        assertThat(preview.destinationIp()).isEqualTo("10.10.10.20");
        assertThat(preview.destinationComponentName()).isEqualTo("BANK-SRV-01");
        assertThat(preview.matchedInterfaceIp()).isEqualTo("10.10.10.20");
        assertThat(preview.capturePoint()).isEqualTo("BANK-FW-01 / WAN");
        assertThat(preview.liveCaptureAllowed()).isTrue();
    }

    @Test
    void captureFailureDoesNotCorruptIncident() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident();
        int eventCount = incident.getEventCount();
        when(captureProvider.startCapture(any())).thenThrow(new CaptureProviderException(
                PacketCaptureFailureCode.TCPDUMP_FAILURE,
                "tcpdump failed"
        ));

        assertThatThrownBy(() -> jobService.start(incident.getId(), new StartPacketCaptureRequest(20, null)))
                .isInstanceOf(ResponseStatusException.class);

        Incident reloaded = incidentRepository.findWithEventsById(incident.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IncidentStatus.ACTIVE);
        assertThat(reloaded.getEventCount()).isEqualTo(eventCount);
        assertThat(reloaded.getTitle()).isEqualTo(incident.getTitle());
        assertThat(analysisRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())).isEmpty();
    }

    private void stubSuccessfulCapture() {
        stubSuccessfulCapture(pcapBytes());
    }

    private void stubSuccessfulCapture(byte[] bytes) {
        when(captureProvider.startCapture(any())).thenReturn(
                new ProviderCaptureHandle("cap-1", PacketCaptureJobStatus.RUNNING)
        );
        when(captureProvider.getCaptureStatus("cap-1")).thenReturn(new ProviderCaptureStatus(
                "cap-1",
                PacketCaptureJobStatus.COMPLETED,
                23,
                (long) bytes.length,
                null,
                null
        ));
        when(captureProvider.retrieveCapture("cap-1")).thenReturn(bytes);
        when(analysisClient.analyze(any(), any(), any())).thenReturn(analysisResponse(23, bytes.length));
    }

    private FastApiPacketCaptureAnalysisResponse analysisResponse(int packets, long bytes) {
        return new FastApiPacketCaptureAnalysisResponse(
                packets,
                bytes,
                List.of("10.0.0.10 (" + packets + ")"),
                List.of("10.10.10.20 (" + packets + ")"),
                List.of("TCP (" + packets + ")"),
                List.of("22 (4)"),
                List.of("Possible port scan or reconnaissance from 10.0.0.10 to 10.10.10.20"),
                "Analyzed " + packets + " packets."
        );
    }

    private byte[] pcapBytes() {
        return new byte[]{(byte) 0xd4, (byte) 0xc3, (byte) 0xb2, (byte) 0xa1, 1, 2, 3, 4};
    }

    private MonitoredComponentResponse bankServerWithServiceLan() {
        MonitoredComponentResponse server = componentService.create(new SaveMonitoredComponentRequest(
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
        return componentService.get(server.id());
    }

    private Incident savePortScanIncident() {
        String deviceId = "fw-pcap-" + UUID.randomUUID().toString().substring(0, 8);
        Instant occurredAt = Instant.now();
        Event event = eventRepository.save(new Event(
                deviceId,
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "FIREWALL_DENY",
                Severity.CRITICAL,
                "DENY TCP 10.0.0.10:1 -> 10.10.10.20:22",
                "protocol=TCP; sourceAddress=10.0.0.10; destinationAddress=10.10.10.20; destinationPort=22",
                "<134>Aug 25 14:00:00 BANK-FW-01 DENY TCP 10.0.0.10:45122 -> 10.10.10.20:22",
                "10.0.0.10",
                "BANK-FW-01",
                "FIREWALL",
                "SYSLOG",
                occurredAt
        ));
        Incident incident = new Incident(
                deviceId + ":SECURITY:" + UUID.randomUUID(),
                "SECURITY",
                event,
                DeviceStatus.WARNING,
                "BANK-FW-01 security alert"
        );
        return incidentRepository.save(incident);
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"operator@aiops.local","password":"operator123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String marker = "\"token\":\"";
        int start = body.indexOf(marker) + marker.length();
        return body.substring(start, body.indexOf('"', start));
    }
}
