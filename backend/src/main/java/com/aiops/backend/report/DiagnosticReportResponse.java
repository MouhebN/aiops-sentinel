package com.aiops.backend.report;

import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Severity;

import java.time.Instant;
import java.util.List;

public record DiagnosticReportResponse(
        Long id,
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
        List<PacketCaptureSummaryResponse> packetCaptureSummaries,
        List<NetworkFlowSummaryResponse> networkFlowSummaries,
        String provider,
        String model,
        String requestedProvider,
        boolean fallbackUsed,
        String fallbackReasonCode,
        String fallbackReason,
        String contextFingerprint,
        boolean cacheHit,
        Long analysisDurationMs,
        Instant generatedAt
) {
    public record PacketCaptureSummaryResponse(
            Long id,
            String fileName,
            int totalPackets,
            long totalBytes,
            List<String> topSourceIps,
            List<String> topDestinationIps,
            List<String> topProtocols,
            List<String> topDestinationPorts,
            List<String> suspiciousFindings,
            String summary,
            Instant createdAt
    ) {
    }

    public record NetworkFlowSummaryResponse(
            String sourceName,
            String sourceIp,
            String destinationIp,
            List<Integer> destinationPorts,
            List<String> protocols,
            int flowCount,
            long totalPackets,
            long totalBytes,
            String anomalyType,
            String anomalyReason,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {
    }
}
