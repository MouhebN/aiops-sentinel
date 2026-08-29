package com.aiops.backend.pcap;

public record PacketCapturePreviewResponse(
        String sourceIp,
        String destinationIp,
        String destinationComponentName,
        String matchedInterfaceName,
        String matchedInterfaceIp,
        String capturePointId,
        String capturePoint,
        String interfaceName,
        int durationSeconds,
        boolean liveCaptureAllowed,
        String message
) {
}
