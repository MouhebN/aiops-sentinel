package com.aiops.backend.metric;

import java.time.Instant;

public record MetricThresholdResponse(
        Long id,
        Long componentId,
        String metricName,
        ThresholdOperator operator,
        double warningValue,
        double criticalValue,
        boolean enabled,
        ThresholdState lastState,
        Instant lastTriggeredAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static MetricThresholdResponse from(MetricThreshold threshold) {
        return new MetricThresholdResponse(
                threshold.getId(),
                threshold.getComponentId(),
                threshold.getMetricName(),
                threshold.getOperator(),
                threshold.getWarningValue(),
                threshold.getCriticalValue(),
                threshold.isEnabled(),
                threshold.getLastState(),
                threshold.getLastTriggeredAt(),
                threshold.getCreatedAt(),
                threshold.getUpdatedAt()
        );
    }
}
