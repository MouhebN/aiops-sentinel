package com.aiops.backend.pcap;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PacketCaptureWindowTests {

    @Test
    void autoRollingWindowIsTriggerMinusPrePlusPost() {
        PacketCaptureJob job = new PacketCaptureJob(
                1L,
                "LAB_SENSOR",
                "10.0.0.10",
                "10.10.10.20",
                "bank-firewall-wan",
                "BANK-FW-01 / WAN",
                "eth1",
                80,
                CaptureTrigger.AUTO_ROLLING,
                60,
                20
        );
        job.markRunning("snap-1");
        PacketCaptureJobResponse response = PacketCaptureJobResponse.from(job, "BANK-SRV-01", "10.10.10.20");
        Instant triggered = response.triggeredAt();
        assertThat(triggered).isEqualTo(job.getStartedAt());
        assertThat(response.captureWindowStart()).isEqualTo(triggered.minusSeconds(60));
        assertThat(response.captureWindowEnd()).isEqualTo(triggered.plusSeconds(20));
    }
}
