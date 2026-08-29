package com.aiops.backend.report;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DiagnosticReportService {

    private final DiagnosticReportRepository reportRepository;
    private final AuditLogService auditLogService;

    public DiagnosticReportService(DiagnosticReportRepository reportRepository, AuditLogService auditLogService) {
        this.reportRepository = reportRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public DiagnosticReportResponse save(SaveDiagnosticReportRequest request) {
        DiagnosticReport report = new DiagnosticReport(
                request.eventId(),
                request.deviceId(),
                request.deviceName(),
                request.deviceType(),
                request.location(),
                request.eventType(),
                request.severity(),
                request.message(),
                request.details(),
                request.occurredAt(),
                request.summary(),
                request.priority(),
                request.impact(),
                request.risk(),
                writeList(request.probableCauses()),
                writeList(request.suggestedActions()),
                writeList(request.diagnosticCommands()),
                writePacketCaptureSummaries(request.packetCaptureSummaries()),
                writeNetworkFlowSummaries(request.networkFlowSummaries()),
                request.provider(),
                request.model(),
                request.requestedProvider(),
                Boolean.TRUE.equals(request.fallbackUsed()),
                request.fallbackReasonCode(),
                request.fallbackReason(),
                request.contextFingerprint(),
                Boolean.TRUE.equals(request.cacheHit()),
                request.analysisDurationMs(),
                Instant.now() // UTC; serialized as ISO-8601 with Z
        );

        DiagnosticReport saved = reportRepository.save(report);
        auditLogService.log(
                AuditAction.REPORT_CREATED,
                "REPORT",
                saved.getId().toString(),
                "Created report for " + saved.getDeviceName()
                        + " provider=" + saved.getProvider()
                        + " requestedProvider=" + saved.getRequestedProvider()
                        + " model=" + saved.getModel()
                        + " fallbackUsed=" + saved.isFallbackUsed()
                        + " fallbackReasonCode=" + saved.getFallbackReasonCode()
                        + " cacheHit=" + saved.isCacheHit()
                        + " analysisDurationMs=" + saved.getAnalysisDurationMs()
                        + " contextFingerprint=" + shortFingerprint(saved.getContextFingerprint())
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DiagnosticReportResponse> list() {
        return reportRepository.findAllByOrderByGeneratedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DiagnosticReportResponse get(Long id) {
        return reportRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnostic report not found"));
    }

    private DiagnosticReportResponse toResponse(DiagnosticReport report) {
        return new DiagnosticReportResponse(
                report.getId(),
                report.getEventId(),
                report.getDeviceId(),
                report.getDeviceName(),
                report.getDeviceType(),
                report.getLocation(),
                report.getEventType(),
                report.getSeverity(),
                report.getMessage(),
                report.getDetails(),
                report.getOccurredAt(),
                report.getSummary(),
                valueOrFallback(report.getPriority(), priorityFromSeverity(report.getSeverity().name())),
                valueOrFallback(report.getImpact(), "Impact was not stored for this older report."),
                report.getRisk(),
                readOptionalList(report.getProbableCausesText()),
                readList(report.getSuggestedActionsText()),
                readList(report.getDiagnosticCommandsText()),
                readPacketCaptureSummaries(report.getPacketCaptureSummariesText()),
                readNetworkFlowSummaries(report.getNetworkFlowSummariesText()),
                report.getProvider(),
                report.getModel(),
                report.getRequestedProvider(),
                report.isFallbackUsed(),
                report.getFallbackReasonCode(),
                report.getFallbackReason(),
                report.getContextFingerprint(),
                report.isCacheHit(),
                report.getAnalysisDurationMs(),
                report.getGeneratedAt()
        );
    }

    private String shortFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return "none";
        }
        return fingerprint.length() <= 12 ? fingerprint : fingerprint.substring(0, 12);
    }

    private String writeList(List<String> values) {
        return String.join("\n", values);
    }

    private String writePacketCaptureSummaries(List<SaveDiagnosticReportRequest.PacketCaptureSummaryRequest> captures) {
        if (captures == null || captures.isEmpty()) {
            return null;
        }
        return captures.stream()
                .map(capture -> String.join("||",
                        String.valueOf(capture.id() == null ? "" : capture.id()),
                        value(capture.fileName()),
                        String.valueOf(capture.totalPackets() == null ? "" : capture.totalPackets()),
                        String.valueOf(capture.totalBytes() == null ? "" : capture.totalBytes()),
                        join(capture.topSourceIps()),
                        join(capture.topDestinationIps()),
                        join(capture.topProtocols()),
                        join(capture.topDestinationPorts()),
                        join(capture.suspiciousFindings()),
                        value(capture.summary()),
                        capture.createdAt() == null ? "" : capture.createdAt().toString()
                ))
                .collect(Collectors.joining("\n"));
    }

    private String writeNetworkFlowSummaries(List<SaveDiagnosticReportRequest.NetworkFlowSummaryRequest> flows) {
        if (flows == null || flows.isEmpty()) {
            return null;
        }
        return flows.stream()
                .map(flow -> String.join("||",
                        value(flow.sourceName()),
                        value(flow.sourceIp()),
                        value(flow.destinationIp()),
                        joinIntegers(flow.destinationPorts()),
                        join(flow.protocols()),
                        String.valueOf(flow.flowCount() == null ? "" : flow.flowCount()),
                        String.valueOf(flow.totalPackets() == null ? "" : flow.totalPackets()),
                        String.valueOf(flow.totalBytes() == null ? "" : flow.totalBytes()),
                        value(flow.anomalyType()),
                        value(flow.anomalyReason()),
                        flow.firstSeenAt() == null ? "" : flow.firstSeenAt().toString(),
                        flow.lastSeenAt() == null ? "" : flow.lastSeenAt().toString()
                ))
                .collect(Collectors.joining("\n"));
    }

    private List<String> readList(String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored report content is invalid");
        }
        return Arrays.stream(text.split("\\R"))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<String> readOptionalList(String text) {
        if (text == null || text.isBlank()) {
            return List.of("This report was generated before probable causes were stored.");
        }
        return Arrays.stream(text.split("\\R"))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<DiagnosticReportResponse.PacketCaptureSummaryResponse> readPacketCaptureSummaries(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R"))
                .filter(line -> !line.isBlank())
                .map(line -> {
                    String[] parts = line.split("\\|\\|", -1);
                    return new DiagnosticReportResponse.PacketCaptureSummaryResponse(
                            parseLong(parts, 0),
                            valueOrNull(parts, 1),
                            parseInt(parts, 2),
                            parseLongValue(parts, 3),
                            splitList(parts, 4),
                            splitList(parts, 5),
                            splitList(parts, 6),
                            splitList(parts, 7),
                            splitList(parts, 8),
                            valueOrNull(parts, 9),
                            parseInstant(parts, 10)
                    );
                })
                .toList();
    }

    private List<DiagnosticReportResponse.NetworkFlowSummaryResponse> readNetworkFlowSummaries(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R"))
                .filter(line -> !line.isBlank())
                .map(line -> {
                    String[] parts = line.split("\\|\\|", -1);
                    return new DiagnosticReportResponse.NetworkFlowSummaryResponse(
                            valueOrNull(parts, 0),
                            valueOrNull(parts, 1),
                            valueOrNull(parts, 2),
                            splitIntegerList(parts, 3),
                            splitList(parts, 4),
                            parseInt(parts, 5),
                            parseLongValue(parts, 6),
                            parseLongValue(parts, 7),
                            valueOrNull(parts, 8),
                            valueOrNull(parts, 9),
                            parseInstant(parts, 10),
                            parseInstant(parts, 11)
                    );
                })
                .toList();
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String priorityFromSeverity(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "P1";
            case "WARNING" -> "P2";
            default -> "P3";
        };
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(";;", values);
    }

    private String joinIntegers(List<Integer> values) {
        return values == null || values.isEmpty()
                ? ""
                : values.stream().map(String::valueOf).collect(Collectors.joining(";;"));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String valueOrNull(String[] parts, int index) {
        if (index >= parts.length || parts[index].isBlank()) {
            return null;
        }
        return parts[index];
    }

    private List<String> splitList(String[] parts, int index) {
        if (index >= parts.length || parts[index].isBlank()) {
            return List.of();
        }
        return Arrays.stream(parts[index].split(";;"))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<Integer> splitIntegerList(String[] parts, int index) {
        if (index >= parts.length || parts[index].isBlank()) {
            return List.of();
        }
        return Arrays.stream(parts[index].split(";;"))
                .filter(value -> !value.isBlank())
                .map(Integer::valueOf)
                .toList();
    }

    private Long parseLong(String[] parts, int index) {
        if (index >= parts.length || parts[index].isBlank()) {
            return null;
        }
        return Long.valueOf(parts[index]);
    }

    private int parseInt(String[] parts, int index) {
        if (index >= parts.length || parts[index].isBlank()) {
            return 0;
        }
        return Integer.parseInt(parts[index]);
    }

    private long parseLongValue(String[] parts, int index) {
        if (index >= parts.length || parts[index].isBlank()) {
            return 0L;
        }
        return Long.parseLong(parts[index]);
    }

    private Instant parseInstant(String[] parts, int index) {
        if (index >= parts.length || parts[index].isBlank()) {
            return null;
        }
        return Instant.parse(parts[index]);
    }
}
