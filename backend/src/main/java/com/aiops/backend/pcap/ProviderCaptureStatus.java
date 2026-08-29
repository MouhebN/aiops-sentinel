package com.aiops.backend.pcap;

public record ProviderCaptureStatus(
        String providerCaptureId,
        PacketCaptureJobStatus status,
        Integer packetCount,
        Long fileSizeBytes,
        PacketCaptureFailureCode failureCode,
        String errorMessage
) {
}
