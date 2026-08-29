package com.aiops.backend.pcap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StartPacketCaptureRequest(
        Integer durationSeconds,
        String capturePointId
) {
}
