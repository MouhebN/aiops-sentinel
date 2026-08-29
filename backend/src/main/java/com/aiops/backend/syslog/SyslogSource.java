package com.aiops.backend.syslog;

import com.aiops.backend.device.DeviceType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "syslog_sources")
public class SyslogSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String expectedHost;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyslogParserProfile parserProfile;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected SyslogSource() {
    }

    public SyslogSource(SaveSyslogSourceRequest request) {
        update(request);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(SaveSyslogSourceRequest request) {
        this.name = request.name().trim();
        this.expectedHost = request.expectedHost().trim();
        this.deviceId = request.deviceId().trim();
        this.deviceName = request.deviceName().trim();
        this.deviceType = request.deviceType();
        this.location = request.location().trim();
        this.parserProfile = request.parserProfile();
        this.enabled = request.enabled() == null || request.enabled();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getExpectedHost() {
        return expectedHost;
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

    public SyslogParserProfile getParserProfile() {
        return parserProfile;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
