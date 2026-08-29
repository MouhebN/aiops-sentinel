package com.aiops.backend.pcap;

public enum PacketCaptureFailureCode {
    PROVIDER_UNAVAILABLE,
    TIMEOUT,
    TCPDUMP_FAILURE,
    EMPTY_CAPTURE,
    TRANSFER_FAILURE,
    ANALYSIS_FAILURE,
    CANCELLED,
    INVALID_REQUEST,
    INCIDENT_RESOLVED,
    BUSY
}
