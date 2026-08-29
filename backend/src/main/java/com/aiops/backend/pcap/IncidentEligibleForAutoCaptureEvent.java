package com.aiops.backend.pcap;

/**
 * Published after commit when an open incident first becomes eligible for automatic PCAP.
 * The listener starts capture asynchronously; publishers must not run tcpdump inline.
 */
public record IncidentEligibleForAutoCaptureEvent(Long incidentId) {
}
