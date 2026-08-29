package com.aiops.backend.pcap;

/**
 * Capture backend used by Sentinel. Implementations may talk to a lab sensor,
 * a firewall API, or an NDR platform. The Spring application never issues
 * tcpdump, Docker, or nsenter commands itself.
 */
public interface PacketCaptureProvider {

    String providerId();

    PacketCaptureProviderHealth health();

    ProviderCaptureHandle startCapture(ProviderCaptureRequest request);

    ProviderCaptureStatus getCaptureStatus(String providerCaptureId);

    byte[] retrieveCapture(String providerCaptureId);

    void cancelCapture(String providerCaptureId);
}
