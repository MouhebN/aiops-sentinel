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
@Table(name = "netflow_import_runs")
public class NetFlowImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sourceId;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NetFlowImportStatus status;

    @Column(nullable = false)
    private long recordsRead;

    @Column(nullable = false)
    private long recordsImported;

    @Column(nullable = false)
    private long suspiciousFlows;

    @Column(nullable = false)
    private long incidentsCreated;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    protected NetFlowImportRun() {
    }

    public NetFlowImportRun(Long sourceId) {
        this.sourceId = sourceId;
        this.status = NetFlowImportStatus.RUNNING;
    }

    @PrePersist
    void onCreate() {
        startedAt = Instant.now();
    }

    public void complete(long recordsRead, long recordsImported, long suspiciousFlows, long incidentsCreated) {
        this.finishedAt = Instant.now();
        this.status = NetFlowImportStatus.SUCCESS;
        this.recordsRead = recordsRead;
        this.recordsImported = recordsImported;
        this.suspiciousFlows = suspiciousFlows;
        this.incidentsCreated = incidentsCreated;
        this.errorMessage = null;
    }

    public void fail(String errorMessage, long recordsRead, long recordsImported) {
        this.finishedAt = Instant.now();
        this.status = NetFlowImportStatus.FAILED;
        this.recordsRead = recordsRead;
        this.recordsImported = recordsImported;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public NetFlowImportStatus getStatus() {
        return status;
    }

    public long getRecordsRead() {
        return recordsRead;
    }

    public long getRecordsImported() {
        return recordsImported;
    }

    public long getSuspiciousFlows() {
        return suspiciousFlows;
    }

    public long getIncidentsCreated() {
        return incidentsCreated;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
