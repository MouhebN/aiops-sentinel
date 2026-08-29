package com.aiops.backend.pcap;

import java.time.Instant;

public record PacketCaptureJobResponse(
        Long id,
        Long incidentId,
        String provider,
        PacketCaptureJobStatus status,
        String sourceIp,
        String destinationIp,
        String destinationComponentName,
        String matchedInterfaceIp,
        String capturePointId,
        String capturePoint,
        String interfaceName,
        int durationSeconds,
        Instant startedAt,
        Instant completedAt,
        Integer packetCount,
        Long fileSizeBytes,
        PacketCaptureFailureCode failureCode,
        String errorMessage,
        Long analysisId,
        Instant createdAt,
        CaptureTrigger trigger,
        int preTriggerSeconds,
        int postTriggerSeconds,
        Instant triggeredAt,
        Instant captureWindowStart,
        Instant captureWindowEnd
) {

    public static PacketCaptureJobResponse from(
            PacketCaptureJob job,
            String destinationComponentName,
            String matchedInterfaceIp
    ) {
        Instant triggeredAt = job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt();
        Instant windowStart = triggeredAt == null ? null : triggeredAt.minusSeconds(Math.max(0, job.getPreTriggerSeconds()));
        Instant windowEnd = triggeredAt == null ? null : triggeredAt.plusSeconds(Math.max(0, job.getPostTriggerSeconds()));
        return new PacketCaptureJobResponse(
                job.getId(),
                job.getIncidentId(),
                job.getProvider(),
                job.getStatus(),
                job.getSourceIp(),
                job.getDestinationIp(),
                destinationComponentName,
                matchedInterfaceIp,
                job.getCapturePointId(),
                job.getCapturePoint(),
                job.getInterfaceName(),
                job.getDurationSeconds(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getPacketCount(),
                job.getFileSizeBytes(),
                job.getFailureCode(),
                job.getErrorMessage(),
                job.getAnalysisId(),
                job.getCreatedAt(),
                job.getTrigger() == null ? CaptureTrigger.MANUAL : job.getTrigger(),
                job.getPreTriggerSeconds(),
                job.getPostTriggerSeconds(),
                triggeredAt,
                windowStart,
                windowEnd
        );
    }
}
