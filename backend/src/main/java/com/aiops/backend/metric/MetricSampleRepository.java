package com.aiops.backend.metric;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MetricSampleRepository extends JpaRepository<MetricSample, Long> {

    List<MetricSample> findByComponentIdAndSampledAtGreaterThanEqualOrderBySampledAtAsc(Long componentId, Instant from);

    long deleteBySampledAtBefore(Instant cutoff);
}
