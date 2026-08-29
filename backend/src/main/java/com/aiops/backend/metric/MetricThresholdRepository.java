package com.aiops.backend.metric;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetricThresholdRepository extends JpaRepository<MetricThreshold, Long> {

    List<MetricThreshold> findByComponentIdOrderByMetricNameAsc(Long componentId);

    List<MetricThreshold> findByComponentIdAndMetricNameAndEnabledTrue(Long componentId, String metricName);

    void deleteByComponentId(Long componentId);
}
