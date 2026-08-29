package com.aiops.backend.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private DeviceType type;

    @Column(nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceStatus status;

    @Column(nullable = false)
    private Instant lastSeenAt;

    protected Device() {
    }

    public Device(String id, String name, DeviceType type, String location, DeviceStatus status, Instant lastSeenAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.location = location;
        this.status = status;
        this.lastSeenAt = lastSeenAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DeviceType getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void updateFromEvent(String name, DeviceType type, String location, DeviceStatus status, Instant occurredAt) {
        this.name = name;
        this.type = type;
        this.location = location;
        this.status = status;
        this.lastSeenAt = occurredAt;
    }
}
