package com.aiops.backend.metric;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "metric_samples")
public class MetricSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long componentId;

    @Column(nullable = false)
    private String componentName;

    @Column(nullable = false)
    private String metricName;

    @Column(nullable = false)
    private double metricValue;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private Instant sampledAt;

    protected MetricSample() {
    }

    public MetricSample(
            Long componentId,
            String componentName,
            String metricName,
            double metricValue,
            String unit,
            String source,
            Instant sampledAt
    ) {
        this.componentId = componentId;
        this.componentName = componentName;
        this.metricName = metricName;
        this.metricValue = metricValue;
        this.unit = unit;
        this.source = source;
        this.sampledAt = sampledAt;
    }

    public Long getId() {
        return id;
    }

    public Long getComponentId() {
        return componentId;
    }

    public String getComponentName() {
        return componentName;
    }

    public String getMetricName() {
        return metricName;
    }

    public double getMetricValue() {
        return metricValue;
    }

    public String getUnit() {
        return unit;
    }

    public String getSource() {
        return source;
    }

    public Instant getSampledAt() {
        return sampledAt;
    }
}
