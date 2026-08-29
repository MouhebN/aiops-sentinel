package com.aiops.backend.pcap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "packet_capture_analyses")
public class PacketCaptureAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private int totalPackets;

    @Column(nullable = false)
    private long totalBytes;

    @Lob
    private String topSourceIpsText;

    @Lob
    private String topDestinationIpsText;

    @Lob
    private String topProtocolsText;

    @Lob
    private String topDestinationPortsText;

    @Lob
    private String suspiciousFindingsText;

    @Lob
    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private Instant createdAt;

    protected PacketCaptureAnalysis() {
    }

    public PacketCaptureAnalysis(
            Long incidentId,
            String fileName,
            String contentType,
            long fileSize,
            int totalPackets,
            long totalBytes,
            String topSourceIpsText,
            String topDestinationIpsText,
            String topProtocolsText,
            String topDestinationPortsText,
            String suspiciousFindingsText,
            String summary
    ) {
        this.incidentId = incidentId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.totalPackets = totalPackets;
        this.totalBytes = totalBytes;
        this.topSourceIpsText = topSourceIpsText;
        this.topDestinationIpsText = topDestinationIpsText;
        this.topProtocolsText = topProtocolsText;
        this.topDestinationPortsText = topDestinationPortsText;
        this.suspiciousFindingsText = suspiciousFindingsText;
        this.summary = summary;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getTotalPackets() {
        return totalPackets;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public String getTopSourceIpsText() {
        return topSourceIpsText;
    }

    public String getTopDestinationIpsText() {
        return topDestinationIpsText;
    }

    public String getTopProtocolsText() {
        return topProtocolsText;
    }

    public String getTopDestinationPortsText() {
        return topDestinationPortsText;
    }

    public String getSuspiciousFindingsText() {
        return suspiciousFindingsText;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
