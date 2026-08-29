package com.aiops.backend;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLog;
import com.aiops.backend.audit.AuditLogRepository;
import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.pcap.PacketCaptureAnalysis;
import com.aiops.backend.pcap.PacketCaptureAnalysisRepository;
import com.aiops.backend.pcap.PacketCaptureAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PacketCaptureAnalysisDeletionTests {

    @Autowired
    private PacketCaptureAnalysisService packetCaptureAnalysisService;

    @Autowired
    private PacketCaptureAnalysisRepository packetCaptureAnalysisRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void deletingAnalysisRemovesOnlyThatRecordAndKeepsTheIncident() {
        Incident incident = saveIncident("keep-incident");
        PacketCaptureAnalysis keep = saveAnalysis(incident.getId(), "keep.pcap");
        PacketCaptureAnalysis remove = saveAnalysis(incident.getId(), "remove.pcap");

        packetCaptureAnalysisService.delete(incident.getId(), remove.getId());

        assertThat(packetCaptureAnalysisRepository.findById(remove.getId())).isEmpty();
        assertThat(packetCaptureAnalysisRepository.findById(keep.getId())).isPresent();
        assertThat(incidentRepository.findById(incident.getId())).isPresent();
        assertThat(packetCaptureAnalysisRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId()))
                .extracting(PacketCaptureAnalysis::getFileName)
                .containsExactly("keep.pcap");

        AuditLog audit = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.PCAP_ANALYSIS_DELETE)
                .filter(log -> remove.getId().toString().equals(log.getTargetId()))
                .findFirst()
                .orElseThrow();
        assertThat(audit.getTargetType()).isEqualTo("PACKET_CAPTURE_ANALYSIS");
        assertThat(audit.getDetails()).contains("incidentId=" + incident.getId());
        assertThat(audit.getDetails()).contains("filename=remove.pcap");
    }

    @Test
    void deletingAnalysisThatBelongsToAnotherIncidentReturnsNotFound() {
        Incident incident = saveIncident("owner");
        Incident otherIncident = saveIncident("other");
        PacketCaptureAnalysis analysis = saveAnalysis(incident.getId(), "owned.pcap");

        assertThatThrownBy(() -> packetCaptureAnalysisService.delete(otherIncident.getId(), analysis.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(packetCaptureAnalysisRepository.findById(analysis.getId())).isPresent();
        assertThat(incidentRepository.findById(incident.getId())).isPresent();
        assertThat(incidentRepository.findById(otherIncident.getId())).isPresent();
    }

    @Test
    void deletingNonexistentAnalysisReturnsNotFound() {
        Incident incident = saveIncident("missing-analysis");

        assertThatThrownBy(() -> packetCaptureAnalysisService.delete(incident.getId(), 9_999_999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(incidentRepository.findById(incident.getId())).isPresent();
    }

    @Test
    void deletingFromNonexistentIncidentReturnsNotFound() {
        Incident incident = saveIncident("existing");
        PacketCaptureAnalysis analysis = saveAnalysis(incident.getId(), "still-here.pcap");

        assertThatThrownBy(() -> packetCaptureAnalysisService.delete(9_999_999L, analysis.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(packetCaptureAnalysisRepository.findById(analysis.getId())).isPresent();
        assertThat(incidentRepository.findById(incident.getId())).isPresent();
    }

    private Incident saveIncident(String suffix) {
        String deviceId = "fw-pcap-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        Event event = eventRepository.save(new Event(
                deviceId,
                deviceId,
                DeviceType.FIREWALL,
                "lab",
                "FIREWALL_DENY",
                Severity.WARNING,
                "DENY TCP 192.168.0.15:1 -> 192.168.0.1:22",
                "sourceAddress=192.168.0.15; destinationAddress=192.168.0.1; destinationPort=22",
                null,
                "192.168.0.15",
                "BANK-FW-01",
                "FIREWALL",
                "SYSLOG",
                Instant.parse("2026-08-19T12:00:00Z")
        ));
        return incidentRepository.save(new Incident(
                deviceId + ":SECURITY",
                "SECURITY",
                event,
                DeviceStatus.WARNING,
                deviceId + " security alert"
        ));
    }

    private PacketCaptureAnalysis saveAnalysis(Long incidentId, String fileName) {
        return packetCaptureAnalysisRepository.save(new PacketCaptureAnalysis(
                incidentId,
                fileName,
                "application/vnd.tcpdump.pcap",
                2048,
                12,
                1800,
                "192.168.0.15 (8)",
                "192.168.0.1 (8)",
                "TCP (12)",
                "22 (4)",
                "Possible port scan or reconnaissance from 192.168.0.15 to 192.168.0.1",
                "Analyzed 12 packets."
        ));
    }
}
