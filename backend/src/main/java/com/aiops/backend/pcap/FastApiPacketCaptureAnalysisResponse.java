package com.aiops.backend.pcap;

import java.util.List;

public record FastApiPacketCaptureAnalysisResponse(
        int totalPackets,
        long totalBytes,
        List<String> topSourceIps,
        List<String> topDestinationIps,
        List<String> topProtocols,
        List<String> topDestinationPorts,
        List<String> suspiciousFindings,
        String summary
) {
}
