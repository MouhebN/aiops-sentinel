package com.aiops.backend.pcap;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record PacketCaptureAnalysisResponse(
        Long id,
        Long incidentId,
        String fileName,
        String contentType,
        long fileSize,
        int totalPackets,
        long totalBytes,
        List<String> topSourceIps,
        List<String> topDestinationIps,
        List<String> topProtocols,
        List<String> topDestinationPorts,
        List<String> suspiciousFindings,
        String summary,
        Instant createdAt,
        long capturedPacketBytes
) {

    public static PacketCaptureAnalysisResponse from(PacketCaptureAnalysis analysis) {
        long captured = analysis.getTotalBytes();
        return new PacketCaptureAnalysisResponse(
                analysis.getId(),
                analysis.getIncidentId(),
                analysis.getFileName(),
                analysis.getContentType(),
                analysis.getFileSize(),
                analysis.getTotalPackets(),
                captured,
                readList(analysis.getTopSourceIpsText()),
                readList(analysis.getTopDestinationIpsText()),
                readList(analysis.getTopProtocolsText()),
                readList(analysis.getTopDestinationPortsText()),
                readList(analysis.getSuspiciousFindingsText()),
                analysis.getSummary(),
                analysis.getCreatedAt(),
                captured
        );
    }

    private static List<String> readList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
