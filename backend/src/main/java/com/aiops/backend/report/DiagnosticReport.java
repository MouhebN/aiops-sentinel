package com.aiops.backend.report;

import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "diagnostic_reports")
public class DiagnosticReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private DeviceType deviceType;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(length = 4000)
    private String details;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 2000)
    private String summary;

    private String priority;

    @Column(length = 4000)
    private String impact;

    @Column(nullable = false, length = 4000)
    private String risk;

    @Lob
    private String probableCausesText;

    @Lob
    @Column(nullable = false)
    private String suggestedActionsText;

    @Lob
    @Column(nullable = false)
    private String diagnosticCommandsText;

    @Lob
    private String packetCaptureSummariesText;

    @Column(columnDefinition = "TEXT")
    private String networkFlowSummariesText;

    @Column(nullable = false)
    private String provider;

    private String model;

    private String requestedProvider;

    private Boolean fallbackUsed;

    private String fallbackReasonCode;

    @Column(length = 500)
    private String fallbackReason;

    @Column(length = 64)
    private String contextFingerprint;

    private Boolean cacheHit;

    private Long analysisDurationMs;

    @Column(nullable = false)
    private Instant generatedAt;

    protected DiagnosticReport() {
    }

    public DiagnosticReport(
            Long eventId,
            String deviceId,
            String deviceName,
            DeviceType deviceType,
            String location,
            String eventType,
            Severity severity,
            String message,
            String details,
            Instant occurredAt,
            String summary,
            String priority,
            String impact,
            String risk,
            String probableCausesText,
            String suggestedActionsText,
            String diagnosticCommandsText,
            String packetCaptureSummariesText,
            String networkFlowSummariesText,
            String provider,
            String model,
            String requestedProvider,
            Boolean fallbackUsed,
            String fallbackReasonCode,
            String fallbackReason,
            String contextFingerprint,
            Boolean cacheHit,
            Long analysisDurationMs,
            Instant generatedAt
    ) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.location = location;
        this.eventType = eventType;
        this.severity = severity;
        this.message = message;
        this.details = details;
        this.occurredAt = occurredAt;
        this.summary = summary;
        this.priority = priority;
        this.impact = impact;
        this.risk = risk;
        this.probableCausesText = probableCausesText;
        this.suggestedActionsText = suggestedActionsText;
        this.diagnosticCommandsText = diagnosticCommandsText;
        this.packetCaptureSummariesText = packetCaptureSummariesText;
        this.networkFlowSummariesText = networkFlowSummariesText;
        this.provider = provider;
        this.model = model;
        this.requestedProvider = requestedProvider;
        this.fallbackUsed = fallbackUsed;
        this.fallbackReasonCode = fallbackReasonCode;
        this.fallbackReason = fallbackReason;
        this.contextFingerprint = contextFingerprint;
        this.cacheHit = cacheHit;
        this.analysisDurationMs = analysisDurationMs;
        this.generatedAt = generatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public String getLocation() {
        return location;
    }

    public String getEventType() {
        return eventType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getSummary() {
        return summary;
    }

    public String getPriority() {
        return priority;
    }

    public String getImpact() {
        return impact;
    }

    public String getRisk() {
        return risk;
    }

    public String getProbableCausesText() {
        return probableCausesText;
    }

    public String getSuggestedActionsText() {
        return suggestedActionsText;
    }

    public String getDiagnosticCommandsText() {
        return diagnosticCommandsText;
    }

    public String getPacketCaptureSummariesText() {
        return packetCaptureSummariesText;
    }

    public String getNetworkFlowSummariesText() {
        return networkFlowSummariesText;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getRequestedProvider() {
        return requestedProvider;
    }

    public boolean isFallbackUsed() {
        return Boolean.TRUE.equals(fallbackUsed);
    }

    public String getFallbackReasonCode() {
        return fallbackReasonCode;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public String getContextFingerprint() {
        return contextFingerprint;
    }

    public boolean isCacheHit() {
        return Boolean.TRUE.equals(cacheHit);
    }

    public Long getAnalysisDurationMs() {
        return analysisDurationMs;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
