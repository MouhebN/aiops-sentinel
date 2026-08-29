package com.aiops.backend.metric;

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
@Table(name = "metric_thresholds")
public class MetricThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long componentId;

    @Column(nullable = false)
    private String metricName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ThresholdOperator operator;

    @Column(nullable = false)
    private double warningValue;

    @Column(nullable = false)
    private double criticalValue;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ThresholdState lastState;

    private Instant lastTriggeredAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected MetricThreshold() {
    }

    public MetricThreshold(
            Long componentId,
            String metricName,
            ThresholdOperator operator,
            double warningValue,
            double criticalValue,
            boolean enabled
    ) {
        this.componentId = componentId;
        this.metricName = metricName;
        this.operator = operator;
        this.warningValue = warningValue;
        this.criticalValue = criticalValue;
        this.enabled = enabled;
        this.lastState = ThresholdState.NORMAL;
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
            String metricName,
            ThresholdOperator operator,
            double warningValue,
            double criticalValue,
            boolean enabled
    ) {
        this.metricName = metricName;
        this.operator = operator;
        this.warningValue = warningValue;
        this.criticalValue = criticalValue;
        this.enabled = enabled;
    }

    public void updateState(ThresholdState state, Instant triggeredAt) {
        this.lastState = state;
        this.lastTriggeredAt = triggeredAt;
    }

    public Long getId() {
        return id;
    }

    public Long getComponentId() {
        return componentId;
    }

    public String getMetricName() {
        return metricName;
    }

    public ThresholdOperator getOperator() {
        return operator;
    }

    public double getWarningValue() {
        return warningValue;
    }

    public double getCriticalValue() {
        return criticalValue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ThresholdState getLastState() {
        return lastState;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
