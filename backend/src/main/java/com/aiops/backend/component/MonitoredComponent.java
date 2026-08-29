package com.aiops.backend.component;

import com.aiops.backend.device.DeviceType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "monitored_components")
public class MonitoredComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private DeviceType type;

    private String ipAddress;

    private String httpUrl;

    private Integer tcpPort;

    private Integer snmpPort;

    @Column(name = "snmp_community")
    private String snmpCommunity;

    private String snmpOid;

    @Column(nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Criticality criticality;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "component_monitoring_methods", joinColumns = @JoinColumn(name = "component_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private Set<MonitoringMethod> monitoringMethods = new LinkedHashSet<>();

    @Column(nullable = false)
    private int checkIntervalSeconds;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComponentStatus lastStatus;

    private Instant lastSeenAt;

    private Instant lastCheckedAt;

    @Column(length = 2000)
    private String lastError;

    @Column(length = 8000)
    private String lastCheckDetails;

    @Column(nullable = false)
    private int failureCount;

    @Column(nullable = false)
    private int successCount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected MonitoredComponent() {
    }

    public MonitoredComponent(
            String name,
            DeviceType type,
            String ipAddress,
            String httpUrl,
            Integer tcpPort,
            Integer snmpPort,
            String snmpCommunity,
            String snmpOid,
            String location,
            Criticality criticality,
            Set<MonitoringMethod> monitoringMethods,
            int checkIntervalSeconds,
            boolean enabled
    ) {
        this.name = name;
        this.type = type;
        this.ipAddress = ipAddress;
        this.httpUrl = httpUrl;
        this.tcpPort = tcpPort;
        this.snmpPort = snmpPort;
        this.snmpCommunity = snmpCommunity;
        this.snmpOid = snmpOid;
        this.location = location;
        this.criticality = criticality;
        this.monitoringMethods = new LinkedHashSet<>(monitoringMethods);
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.enabled = enabled;
        this.lastStatus = ComponentStatus.UNKNOWN;
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

    public void updateConfiguration(
            String name,
            DeviceType type,
            String ipAddress,
            String httpUrl,
            Integer tcpPort,
            Integer snmpPort,
            String snmpCommunity,
            String snmpOid,
            String location,
            Criticality criticality,
            Set<MonitoringMethod> monitoringMethods,
            int checkIntervalSeconds,
            boolean enabled
    ) {
        this.name = name;
        this.type = type;
        this.ipAddress = ipAddress;
        this.httpUrl = httpUrl;
        this.tcpPort = tcpPort;
        this.snmpPort = snmpPort;
        this.snmpCommunity = snmpCommunity;
        this.snmpOid = snmpOid;
        this.location = location;
        this.criticality = criticality;
        this.monitoringMethods = new LinkedHashSet<>(monitoringMethods);
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.enabled = enabled;
    }

    public void enable() {
        this.enabled = true;
        this.lastCheckedAt = null;
    }

    public void disable() {
        this.enabled = false;
    }

    public void updateStatus(ComponentStatus status, Instant checkedAt, Instant seenAt, String error, String checkDetails) {
        this.lastStatus = status;
        this.lastCheckedAt = checkedAt == null ? Instant.now() : checkedAt;
        this.lastSeenAt = seenAt;
        this.lastError = error;
        this.lastCheckDetails = checkDetails;
        if (status == ComponentStatus.UP) {
            successCount++;
        } else if (status == ComponentStatus.DOWN || status == ComponentStatus.DEGRADED) {
            failureCount++;
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DeviceType getType() {
        return type;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getHttpUrl() {
        return httpUrl;
    }

    public Integer getTcpPort() {
        return tcpPort;
    }

    public Integer getSnmpPort() {
        return snmpPort;
    }

    public String getSnmpCommunity() {
        return snmpCommunity;
    }

    public String getSnmpOid() {
        return snmpOid;
    }

    public String getLocation() {
        return location;
    }

    public Criticality getCriticality() {
        return criticality;
    }

    public Set<MonitoringMethod> getMonitoringMethods() {
        return monitoringMethods;
    }

    public int getCheckIntervalSeconds() {
        return checkIntervalSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ComponentStatus getLastStatus() {
        return lastStatus;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getLastCheckDetails() {
        return lastCheckDetails;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
