package com.aiops.backend.pcap;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PacketCaptureSizeSemanticsTests {

    @Test
    void fileSizeAndCapturedPacketBytesRemainDistinct() {
        PacketCaptureAnalysisResponse response = new PacketCaptureAnalysisResponse(
                1L,
                45L,
                "scan.pcap",
                "application/vnd.tcpdump.pcap",
                654,
                7,
                518,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "Analyzed 7 packets.",
                Instant.parse("2026-08-26T14:00:00Z"),
                518
        );

        assertThat(response.fileSize()).isEqualTo(654L);
        assertThat(response.totalBytes()).isEqualTo(518L);
        assertThat(response.capturedPacketBytes()).isEqualTo(518L);
        assertThat(response.fileSize()).isNotEqualTo(response.capturedPacketBytes());
    }
}
