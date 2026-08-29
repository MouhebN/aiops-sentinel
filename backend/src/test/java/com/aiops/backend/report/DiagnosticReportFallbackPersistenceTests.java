package com.aiops.backend.report;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLog;
import com.aiops.backend.audit.AuditLogRepository;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DiagnosticReportFallbackPersistenceTests {

    @Autowired
    private DiagnosticReportService reportService;

    @Autowired
    private DiagnosticReportRepository reportRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void persistedFallbackReportKeepsOllamaFailureMetadata() {
        SaveDiagnosticReportRequest request = new SaveDiagnosticReportRequest(
                12L,
                "fw-01",
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "PORT_SCAN",
                Severity.WARNING,
                "Possible port scan from 192.168.0.15 to 192.168.0.1",
                "NetFlow PORT_SCAN",
                Instant.parse("2026-08-20T12:00:00Z"),
                "Rules fallback diagnostic for a possible port scan.",
                "P2",
                "Reconnaissance may precede further probing.",
                "Risk of unauthorized access if scanning continues.",
                List.of("Port scanning from 192.168.0.15"),
                List.of("Review firewall counters"),
                List.of("ping 192.168.0.1"),
                List.of(),
                List.of(),
                "rules",
                "qwen3:8b",
                "ollama",
                true,
                "OLLAMA_TIMEOUT",
                "The local AI model did not respond within the configured timeout."
        );

        DiagnosticReportResponse saved = reportService.save(request);
        DiagnosticReport stored = reportRepository.findById(saved.id()).orElseThrow();
        DiagnosticReportResponse loaded = reportService.get(saved.id());

        assertThat(stored.getProvider()).isEqualTo("rules");
        assertThat(stored.getRequestedProvider()).isEqualTo("ollama");
        assertThat(stored.getModel()).isEqualTo("qwen3:8b");
        assertThat(stored.isFallbackUsed()).isTrue();
        assertThat(stored.getFallbackReasonCode()).isEqualTo("OLLAMA_TIMEOUT");
        assertThat(stored.getFallbackReason()).contains("timeout");

        assertThat(loaded.provider()).isEqualTo("rules");
        assertThat(loaded.requestedProvider()).isEqualTo("ollama");
        assertThat(loaded.fallbackUsed()).isTrue();
        assertThat(loaded.fallbackReasonCode()).isEqualTo("OLLAMA_TIMEOUT");
        assertThat(loaded.model()).isEqualTo("qwen3:8b");

        AuditLog audit = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.REPORT_CREATED)
                .filter(log -> saved.id().toString().equals(log.getTargetId()))
                .findFirst()
                .orElseThrow();
        assertThat(audit.getDetails()).contains("provider=rules");
        assertThat(audit.getDetails()).contains("requestedProvider=ollama");
        assertThat(audit.getDetails()).contains("fallbackUsed=true");
        assertThat(audit.getDetails()).contains("fallbackReasonCode=OLLAMA_TIMEOUT");
    }

    @Test
    void ollamaSuccessIsNotStoredAsFallback() {
        SaveDiagnosticReportRequest request = new SaveDiagnosticReportRequest(
                13L,
                "fw-01",
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "PORT_SCAN",
                Severity.WARNING,
                "Possible port scan from 192.168.0.15 to 192.168.0.1",
                null,
                Instant.parse("2026-08-20T12:00:00Z"),
                "NetFlow and PCAP corroborate reconnaissance.",
                "P2",
                "No service outage established.",
                "Risk of unauthorized access.",
                List.of("Port scanning"),
                List.of("Review source 192.168.0.15"),
                List.of("ss -lntp"),
                List.of(),
                List.of(),
                "ollama",
                "qwen3:8b",
                "ollama",
                false,
                null,
                null
        );

        DiagnosticReportResponse saved = reportService.save(request);
        assertThat(saved.fallbackUsed()).isFalse();
        assertThat(saved.fallbackReasonCode()).isNull();
        assertThat(saved.provider()).isEqualTo("ollama");
    }

    @Test
    void persistedReportKeepsCacheMetadata() {
        String fingerprint = "a".repeat(64);
        SaveDiagnosticReportRequest request = new SaveDiagnosticReportRequest(
                14L,
                "fw-01",
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "PORT_SCAN",
                Severity.WARNING,
                "Possible port scan from 192.168.0.15 to 192.168.0.1",
                null,
                Instant.parse("2026-08-20T12:00:00Z"),
                "Cached Ollama diagnostic for a possible port scan.",
                "P2",
                "No service outage established.",
                "Risk of unauthorized access.",
                List.of("Port scanning"),
                List.of("Review source 192.168.0.15"),
                List.of("ss -lntp"),
                List.of(),
                List.of(),
                "ollama",
                "qwen3:8b",
                "ollama",
                false,
                null,
                null,
                fingerprint,
                true,
                120L
        );

        DiagnosticReportResponse saved = reportService.save(request);
        DiagnosticReportResponse loaded = reportService.get(saved.id());
        assertThat(loaded.cacheHit()).isTrue();
        assertThat(loaded.contextFingerprint()).isEqualTo(fingerprint);
        assertThat(loaded.analysisDurationMs()).isEqualTo(120L);

        AuditLog audit = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.REPORT_CREATED)
                .filter(log -> saved.id().toString().equals(log.getTargetId()))
                .findFirst()
                .orElseThrow();
        assertThat(audit.getDetails()).contains("cacheHit=true");
        assertThat(audit.getDetails()).contains("analysisDurationMs=120");
        assertThat(audit.getDetails()).contains("contextFingerprint=" + fingerprint.substring(0, 12));
    }
}
