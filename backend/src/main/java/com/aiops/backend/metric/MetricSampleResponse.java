package com.aiops.backend.metric;

import java.time.Instant;

public record MetricSampleResponse(
        Long id,
        Long componentId,
        String componentName,
        String metricName,
        double metricValue,
        String unit,
        String source,
        Instant sampledAt
) {

    public static MetricSampleResponse from(MetricSample sample) {
        return new MetricSampleResponse(
                sample.getId(),
                sample.getComponentId(),
                sample.getComponentName(),
                sample.getMetricName(),
                sample.getMetricValue(),
                sample.getUnit(),
                sample.getSource(),
                sample.getSampledAt()
        );
    }
}
