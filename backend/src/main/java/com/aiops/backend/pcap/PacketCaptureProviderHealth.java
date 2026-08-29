package com.aiops.backend.pcap;

public record PacketCaptureProviderHealth(
        boolean available,
        String status,
        String message,
        Boolean rollingEnabled,
        Boolean rollingRunning
) {
    public PacketCaptureProviderHealth(boolean available, String status, String message) {
        this(available, status, message, null, null);
    }
}
