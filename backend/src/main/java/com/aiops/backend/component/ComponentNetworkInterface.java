package com.aiops.backend.component;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "component_network_interfaces")
public class ComponentNetworkInterface {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "monitored_component_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MonitoredComponent monitoredComponent;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "ip_address", nullable = false, unique = true, length = 64)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NetworkInterfaceRole role;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(nullable = false)
    private Instant createdAt;

    protected ComponentNetworkInterface() {
    }

    public ComponentNetworkInterface(
            MonitoredComponent monitoredComponent,
            String name,
            String ipAddress,
            NetworkInterfaceRole role,
            boolean primary
    ) {
        this.monitoredComponent = monitoredComponent;
        this.name = name;
        this.ipAddress = ipAddress;
        this.role = role == null ? NetworkInterfaceRole.OTHER : role;
        this.primary = primary;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void update(String name, String ipAddress, NetworkInterfaceRole role, boolean primary) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.role = role == null ? NetworkInterfaceRole.OTHER : role;
        this.primary = primary;
    }

    public Long getId() {
        return id;
    }

    public MonitoredComponent getMonitoredComponent() {
        return monitoredComponent;
    }

    public String getName() {
        return name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public NetworkInterfaceRole getRole() {
        return role;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
