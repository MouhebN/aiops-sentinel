package com.aiops.backend.pcap;

public record ProviderCaptureRequest(
        int durationSeconds,
        String interfaceName,
        String sourceIp,
        String destinationIp,
        String capturePointId,
        String mode,
        Integer preTriggerSeconds,
        Integer postTriggerSeconds
) {
    public ProviderCaptureRequest(
            int durationSeconds,
            String interfaceName,
            String sourceIp,
            String destinationIp,
            String capturePointId
    ) {
        this(durationSeconds, interfaceName, sourceIp, destinationIp, capturePointId, "ON_DEMAND", null, null);
    }

    public boolean rollingSnapshot() {
        return "ROLLING_SNAPSHOT".equalsIgnoreCase(mode);
    }
}
