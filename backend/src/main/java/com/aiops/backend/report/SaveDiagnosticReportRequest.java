package com.aiops.backend.report;

import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record SaveDiagnosticReportRequest(
        Long eventId,
        @NotBlank String deviceId,
        @NotBlank String deviceName,
        @NotNull DeviceType deviceType,
        @NotBlank String location,
        @NotBlank String eventType,
        @NotNull Severity severity,
        @NotBlank @Size(max = 1000) String message,
        @Size(max = 4000) String details,
        @NotNull Instant occurredAt,
        @NotBlank @Size(max = 2000) String summary,
        @NotBlank String priority,
        @NotBlank @Size(max = 4000) String impact,
        @NotBlank @Size(max = 4000) String risk,
        @NotEmpty List<@NotBlank String> probableCauses,
        @NotEmpty List<@NotBlank String> suggestedActions,
        @NotEmpty List<@NotBlank String> diagnosticCommands,
        List<PacketCaptureSummaryRequest> packetCaptureSummaries,
        List<NetworkFlowSummaryRequest> networkFlowSummaries,
        @NotBlank String provider,
        String model,
        String requestedProvider,
        Boolean fallbackUsed,
        String fallbackReasonCode,
        @Size(max = 500) String fallbackReason,
        @Size(max = 64) String contextFingerprint,
        Boolean cacheHit,
        Long analysisDurationMs
) {
    public SaveDiagnosticReportRequest(
            Long eventId,
            String deviceId,
            String deviceName,
            DeviceType deviceType,
            String location,
            String eventType,
            Severity severity,
            String message,
            String details,
            Instant occurredAt,
            String summary,
            String priority,
            String impact,
            String risk,
            List<String> probableCauses,
            List<String> suggestedActions,
            List<String> diagnosticCommands,
            List<PacketCaptureSummaryRequest> packetCaptureSummaries,
            List<NetworkFlowSummaryRequest> networkFlowSummaries,
            String provider,
            String model
    ) {
        this(
                eventId,
                deviceId,
                deviceName,
                deviceType,
                location,
                eventType,
                severity,
                message,
                details,
                occurredAt,
                summary,
                priority,
                impact,
                risk,
                probableCauses,
                suggestedActions,
                diagnosticCommands,
                packetCaptureSummaries,
                networkFlowSummaries,
                provider,
                model,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public SaveDiagnosticReportRequest(
            Long eventId,
            String deviceId,
            String deviceName,
            DeviceType deviceType,
            String location,
            String eventType,
            Severity severity,
            String message,
            String details,
            Instant occurredAt,
            String summary,
            String priority,
            String impact,
            String risk,
            List<String> probableCauses,
            List<String> suggestedActions,
            List<String> diagnosticCommands,
            List<PacketCaptureSummaryRequest> packetCaptureSummaries,
            List<NetworkFlowSummaryRequest> networkFlowSummaries,
            String provider,
            String model,
            String requestedProvider,
            Boolean fallbackUsed,
            String fallbackReasonCode,
            String fallbackReason
    ) {
        this(
                eventId,
                deviceId,
                deviceName,
                deviceType,
                location,
                eventType,
                severity,
                message,
                details,
                occurredAt,
                summary,
                priority,
                impact,
                risk,
                probableCauses,
                suggestedActions,
                diagnosticCommands,
                packetCaptureSummaries,
                networkFlowSummaries,
                provider,
                model,
                requestedProvider,
                fallbackUsed,
                fallbackReasonCode,
                fallbackReason,
                null,
                null,
                null
        );
    }

    public record PacketCaptureSummaryRequest(
            Long id,
            String fileName,
            Integer totalPackets,
            Long totalBytes,
            List<String> topSourceIps,
            List<String> topDestinationIps,
            List<String> topProtocols,
            List<String> topDestinationPorts,
            List<String> suspiciousFindings,
            String summary,
            Instant createdAt
    ) {
    }

    public record NetworkFlowSummaryRequest(
            String sourceName,
            String sourceIp,
            String destinationIp,
            List<Integer> destinationPorts,
            List<String> protocols,
            Integer flowCount,
            Long totalPackets,
            Long totalBytes,
            String anomalyType,
            String anomalyReason,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {
    }
}
