package com.aiops.backend.pcap;

public record ProviderCaptureHandle(
        String providerCaptureId,
        PacketCaptureJobStatus status
) {
}
