package com.aiops.backend.pcap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "packet_capture_jobs")
public class PacketCaptureJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false, length = 40)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PacketCaptureJobStatus status;

    @Column(length = 64)
    private String sourceIp;

    @Column(length = 64)
    private String destinationIp;

    @Column(length = 120)
    private String capturePointId;

    @Column(length = 160)
    private String capturePoint;

    @Column(length = 32)
    private String interfaceName;

    @Column(nullable = false)
    private int durationSeconds;

    private Instant startedAt;
    private Instant completedAt;
    private Integer packetCount;
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private PacketCaptureFailureCode failureCode;

    @Column(length = 1000)
    private String errorMessage;

    private Long analysisId;

    @Column(length = 80)
    private String providerCaptureId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_trigger", nullable = false, length = 32)
    private CaptureTrigger trigger = CaptureTrigger.MANUAL;

    @Column(nullable = false)
    private int preTriggerSeconds;

    @Column(nullable = false)
    private int postTriggerSeconds;

    @Column(nullable = false)
    private Instant createdAt;

    protected PacketCaptureJob() {
    }

    public PacketCaptureJob(
            Long incidentId,
            String provider,
            String sourceIp,
            String destinationIp,
            String capturePointId,
            String capturePoint,
            String interfaceName,
            int durationSeconds
    ) {
        this(incidentId, provider, sourceIp, destinationIp, capturePointId, capturePoint, interfaceName,
                durationSeconds, CaptureTrigger.MANUAL, 0, durationSeconds);
    }

    public PacketCaptureJob(
            Long incidentId,
            String provider,
            String sourceIp,
            String destinationIp,
            String capturePointId,
            String capturePoint,
            String interfaceName,
            int durationSeconds,
            CaptureTrigger trigger,
            int preTriggerSeconds,
            int postTriggerSeconds
    ) {
        this.incidentId = incidentId;
        this.provider = provider;
        this.status = PacketCaptureJobStatus.PENDING;
        this.sourceIp = sourceIp;
        this.destinationIp = destinationIp;
        this.capturePointId = capturePointId;
        this.capturePoint = capturePoint;
        this.interfaceName = interfaceName;
        this.durationSeconds = durationSeconds;
        this.trigger = trigger == null ? CaptureTrigger.MANUAL : trigger;
        this.preTriggerSeconds = Math.max(0, preTriggerSeconds);
        this.postTriggerSeconds = postTriggerSeconds > 0 ? postTriggerSeconds : durationSeconds;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markRunning(String providerCaptureId) {
        this.status = PacketCaptureJobStatus.RUNNING;
        this.providerCaptureId = providerCaptureId;
        this.startedAt = Instant.now();
    }

    public void updateProgress(Integer packetCount, Long fileSizeBytes) {
        if (packetCount != null) {
            this.packetCount = packetCount;
        }
        if (fileSizeBytes != null) {
            this.fileSizeBytes = fileSizeBytes;
        }
    }

    public void complete(int packetCount, long fileSizeBytes, Long analysisId) {
        this.status = PacketCaptureJobStatus.COMPLETED;
        this.packetCount = packetCount;
        this.fileSizeBytes = fileSizeBytes;
        this.analysisId = analysisId;
        this.completedAt = Instant.now();
        this.failureCode = null;
        this.errorMessage = null;
    }

    public void fail(PacketCaptureFailureCode code, String message) {
        this.status = PacketCaptureJobStatus.FAILED;
        this.failureCode = code;
        this.errorMessage = message == null ? null : message.substring(0, Math.min(message.length(), 1000));
        this.completedAt = Instant.now();
    }

    public void cancel(String message) {
        this.status = PacketCaptureJobStatus.CANCELLED;
        this.failureCode = PacketCaptureFailureCode.CANCELLED;
        this.errorMessage = message;
        this.completedAt = Instant.now();
    }

    public boolean isActive() {
        return status == PacketCaptureJobStatus.PENDING || status == PacketCaptureJobStatus.RUNNING;
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getProvider() {
        return provider;
    }

    public PacketCaptureJobStatus getStatus() {
        return status;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getDestinationIp() {
        return destinationIp;
    }

    public String getCapturePointId() {
        return capturePointId;
    }

    public String getCapturePoint() {
        return capturePoint;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getPacketCount() {
        return packetCount;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public PacketCaptureFailureCode getFailureCode() {
        return failureCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getAnalysisId() {
        return analysisId;
    }

    public String getProviderCaptureId() {
        return providerCaptureId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public CaptureTrigger getTrigger() {
        return trigger;
    }

    public int getPreTriggerSeconds() {
        return preTriggerSeconds;
    }

    public int getPostTriggerSeconds() {
        return postTriggerSeconds;
    }
}
