package com.aiops.backend.netflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "netflow_sources")
public class NetFlowSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NetFlowProviderType providerType;

    @Column(nullable = false)
    private String dataDirectory;

    @Column(nullable = false)
    private int collectorPort;

    @Column(nullable = false)
    private boolean enabled;

    private Instant lastImportAt;

    @Enumerated(EnumType.STRING)
    private NetFlowImportStatus lastImportStatus;

    @Column(columnDefinition = "TEXT")
    private String lastImportMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected NetFlowSource() {
    }

    public NetFlowSource(String name, String dataDirectory, int collectorPort, boolean enabled) {
        this.name = name;
        this.providerType = NetFlowProviderType.NFDUMP;
        this.dataDirectory = dataDirectory;
        this.collectorPort = collectorPort;
        this.enabled = enabled;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void update(SaveNetFlowSourceRequest request) {
        this.name = request.name().trim();
        this.providerType = request.providerType() == null ? NetFlowProviderType.NFDUMP : request.providerType();
        this.dataDirectory = request.dataDirectory().trim();
        this.collectorPort = request.collectorPort();
        this.enabled = request.enabled() == null || request.enabled();
    }

    public void applyConfiguredDefaults(String dataDirectory, int collectorPort, boolean enabled) {
        this.dataDirectory = dataDirectory;
        this.collectorPort = collectorPort;
        this.enabled = enabled;
    }

    public void recordImport(NetFlowImportStatus status, String message, Instant importedAt) {
        this.lastImportStatus = status;
        this.lastImportMessage = message;
        this.lastImportAt = importedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public NetFlowProviderType getProviderType() {
        return providerType;
    }

    public String getDataDirectory() {
        return dataDirectory;
    }

    public int getCollectorPort() {
        return collectorPort;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getLastImportAt() {
        return lastImportAt;
    }

    public NetFlowImportStatus getLastImportStatus() {
        return lastImportStatus;
    }

    public String getLastImportMessage() {
        return lastImportMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
