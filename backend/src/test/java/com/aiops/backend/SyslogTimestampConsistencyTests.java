package com.aiops.backend;

import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.report.DiagnosticReportResponse;
import com.aiops.backend.report.DiagnosticReportService;
import com.aiops.backend.report.SaveDiagnosticReportRequest;
import com.aiops.backend.syslog.SaveSyslogSourceRequest;
import com.aiops.backend.syslog.SyslogIngestionService;
import com.aiops.backend.syslog.SyslogParserProfile;
import com.aiops.backend.syslog.SyslogSource;
import com.aiops.backend.syslog.SyslogSourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SyslogTimestampConsistencyTests {

    private static final ZoneId DEPLOYMENT_ZONE = ZoneId.of("Africa/Tunis");
    private static final DateTimeFormatter SYSLOG_CLOCK = DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.ENGLISH);

    @Autowired
    private SyslogIngestionService syslogIngestionService;

    @Autowired
    private SyslogSourceRepository syslogSourceRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private DiagnosticReportService diagnosticReportService;

    @Test
    void newlyReceivedSyslogOccurredAtIsNotAfterReportGeneratedAt() {
        String host = "fw-ts-" + UUID.randomUUID().toString().substring(0, 8);
        syslogSourceRepository.save(new SyslogSource(new SaveSyslogSourceRequest(
                host,
                host,
                host,
                host,
                DeviceType.FIREWALL,
                "lab",
                SyslogParserProfile.FIREWALL,
                true
        )));

        ZonedDateTime occurredLocal = ZonedDateTime.now(DEPLOYMENT_ZONE).minusMinutes(5);
        String rawLog = "<134>" + occurredLocal.format(SYSLOG_CLOCK) + " " + host
                + " DENY TCP 192.168.0.15:45122 -> 192.168.0.1:22";
        syslogIngestionService.ingest("203.0.113.10", rawLog);

        Event event = eventRepository.findAll().stream()
                .filter(saved -> host.equals(saved.getDeviceId()))
                .findFirst()
                .orElseThrow();
        DiagnosticReportResponse report = diagnosticReportService.save(new SaveDiagnosticReportRequest(
                event.getId(),
                event.getDeviceId(),
                event.getDeviceName(),
                event.getDeviceType(),
                event.getLocation(),
                event.getEventType(),
                event.getSeverity(),
                event.getMessage(),
                event.getDetails(),
                event.getOccurredAt(),
                "Firewall denied a connection from 192.168.0.15.",
                "P2",
                "Unauthorized access attempt was blocked.",
                "Repeated denials may indicate reconnaissance.",
                List.of("Unsolicited inbound TCP connection"),
                List.of("Review firewall deny logs"),
                List.of("show log | include 192.168.0.15"),
                List.of(),
                List.of(),
                "rules",
                null
        ));

        assertThat(event.getOccurredAt()).isBeforeOrEqualTo(report.generatedAt());
        assertThat(event.getOccurredAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(event.getOccurredAt().toString()).endsWith("Z");
        assertThat(report.generatedAt().toString()).contains("T").endsWith("Z");
    }

    @Test
    void incidentFirstSeenAtIsNotAfterLastSeenAtForSuccessiveSyslogEvents() {
        String host = "fw-ts-" + UUID.randomUUID().toString().substring(0, 8);
        syslogSourceRepository.save(new SyslogSource(new SaveSyslogSourceRequest(
                host,
                host,
                host,
                host,
                DeviceType.FIREWALL,
                "lab",
                SyslogParserProfile.FIREWALL,
                true
        )));

        ZonedDateTime first = ZonedDateTime.now(DEPLOYMENT_ZONE).minusMinutes(6);
        ZonedDateTime second = first.plusMinutes(2);
        syslogIngestionService.ingest(
                "203.0.113.10",
                "<134>" + first.format(SYSLOG_CLOCK) + " " + host + " DENY TCP 192.168.0.15:45122 -> 192.168.0.1:22"
        );
        syslogIngestionService.ingest(
                "203.0.113.10",
                "<134>" + second.format(SYSLOG_CLOCK) + " " + host + " DENY TCP 192.168.0.15:45123 -> 192.168.0.1:80"
        );

        Incident incident = incidentRepository.findAll().stream()
                .filter(saved -> host.equals(saved.getDeviceId()))
                .findFirst()
                .orElseThrow();
        assertThat(incident.getFirstSeenAt()).isBeforeOrEqualTo(incident.getLastSeenAt());
        assertThat(incident.getEventCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void rfc3164TimestampIsNotInterpretedAsUtcWhenDeploymentZoneIsAhead() {
        String host = "fw-ts-" + UUID.randomUUID().toString().substring(0, 8);
        syslogSourceRepository.save(new SyslogSource(new SaveSyslogSourceRequest(
                host,
                host,
                host,
                host,
                DeviceType.FIREWALL,
                "lab",
                SyslogParserProfile.FIREWALL,
                true
        )));

        syslogIngestionService.ingest(
                "203.0.113.10",
                "<134>Aug 18 16:16:00 " + host + " DENY TCP 192.168.0.15:45122 -> 192.168.0.1:22"
        );

        Event event = eventRepository.findAll().stream()
                .filter(saved -> host.equals(saved.getDeviceId()))
                .findFirst()
                .orElseThrow();
        ZonedDateTime now = ZonedDateTime.now(DEPLOYMENT_ZONE);
        ZonedDateTime expected = LocalDateTime.of(now.getYear(), 8, 18, 16, 16)
                .atZone(DEPLOYMENT_ZONE);
        if (expected.isAfter(now.plusDays(1))) {
            expected = expected.withYear(now.getYear() - 1);
        }
        assertThat(event.getOccurredAt()).isEqualTo(expected.toInstant());
        assertThat(event.getSeverity()).isEqualTo(Severity.WARNING);
    }
}
