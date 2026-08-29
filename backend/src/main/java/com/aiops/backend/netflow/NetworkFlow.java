package com.aiops.backend.netflow;

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
@Table(name = "network_flows")
public class NetworkFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sourceId;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Column(nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private String sourceIp;

    @Column(nullable = false)
    private String destinationIp;

    private Integer sourcePort;

    private Integer destinationPort;

    @Column(nullable = false)
    private String protocol;

    @Column(nullable = false)
    private long packets;

    @Column(nullable = false)
    private long bytes;

    private String exporterName;

    private Integer inputInterface;

    private Integer outputInterface;

    @Column(nullable = false)
    private boolean suspicious;

    @Enumerated(EnumType.STRING)
    private NetFlowAnomalyType anomalyType;

    @Column(columnDefinition = "TEXT")
    private String anomalyReason;

    private Long incidentId;

    @Column(nullable = false, unique = true, length = 64)
    private String recordHash;

    @Column(columnDefinition = "TEXT")
    private String rawRecord;

    @Column(nullable = false)
    private Instant createdAt;

    protected NetworkFlow() {
    }

    public NetworkFlow(
            Long sourceId,
            Instant startTime,
            Instant endTime,
            long durationMs,
            String sourceIp,
            String destinationIp,
            Integer sourcePort,
            Integer destinationPort,
            String protocol,
            long packets,
            long bytes,
            String exporterName,
            Integer inputInterface,
            Integer outputInterface,
            String recordHash,
            String rawRecord
    ) {
        this.sourceId = sourceId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMs = durationMs;
        this.sourceIp = sourceIp;
        this.destinationIp = destinationIp;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.protocol = protocol;
        this.packets = packets;
        this.bytes = bytes;
        this.exporterName = exporterName;
        this.inputInterface = inputInterface;
        this.outputInterface = outputInterface;
        this.recordHash = recordHash;
        this.rawRecord = rawRecord;
        this.suspicious = false;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markSuspicious(NetFlowAnomalyType anomalyType, String anomalyReason, Long incidentId) {
        this.suspicious = true;
        this.anomalyType = anomalyType;
        this.anomalyReason = anomalyReason;
        if (incidentId != null) {
            this.incidentId = incidentId;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getDestinationIp() {
        return destinationIp;
    }

    public Integer getSourcePort() {
        return sourcePort;
    }

    public Integer getDestinationPort() {
        return destinationPort;
    }

    public String getProtocol() {
        return protocol;
    }

    public long getPackets() {
        return packets;
    }

    public long getBytes() {
        return bytes;
    }

    public String getExporterName() {
        return exporterName;
    }

    public Integer getInputInterface() {
        return inputInterface;
    }

    public Integer getOutputInterface() {
        return outputInterface;
    }

    public boolean isSuspicious() {
        return suspicious;
    }

    public NetFlowAnomalyType getAnomalyType() {
        return anomalyType;
    }

    public String getAnomalyReason() {
        return anomalyReason;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getRecordHash() {
        return recordHash;
    }

    public String getRawRecord() {
        return rawRecord;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
