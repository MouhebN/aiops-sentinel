package com.aiops.backend.event;

import com.aiops.backend.device.DeviceType;
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
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Lob
    private String rawLog;

    @Column(length = 120)
    private String sourceIp;

    @Column(length = 120)
    private String syslogSourceName;

    @Column(length = 60)
    private String parsingProfile;

    @Column(length = 40)
    private String eventSource;

    @Column(nullable = false)
    private Instant occurredAt;

    protected Event() {
    }

    public Event(
            String deviceId,
            String deviceName,
            DeviceType deviceType,
            String location,
            String eventType,
            Severity severity,
            String message,
            String details,
            String rawLog,
            String sourceIp,
            String syslogSourceName,
            String parsingProfile,
            String eventSource,
            Instant occurredAt
    ) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.location = location;
        this.eventType = eventType;
        this.severity = severity;
        this.message = message;
        this.details = details;
        this.rawLog = rawLog;
        this.sourceIp = sourceIp;
        this.syslogSourceName = syslogSourceName;
        this.parsingProfile = parsingProfile;
        this.eventSource = eventSource;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
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

    public String getRawLog() {
        return rawLog;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getSyslogSourceName() {
        return syslogSourceName;
    }

    public String getParsingProfile() {
        return parsingProfile;
    }

    public String getEventSource() {
        return eventSource;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
