package com.aiops.backend.metric;

import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.EventIngestionRequest;
import com.aiops.backend.event.EventService;
import com.aiops.backend.event.Severity;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MetricSampleService {

    private static final Duration METRIC_RETENTION = Duration.ofDays(30);

    private static final Pattern NUMERIC_KEY_VALUE = Pattern.compile(
            "\\b([A-Za-z][A-Za-z0-9]*)=(-?\\d+(?:\\.\\d+)?)"
    );

    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("cpuUsagePercent", "%"),
            Map.entry("cpuUserPercent", "%"),
            Map.entry("cpuSystemPercent", "%"),
            Map.entry("memoryUsedPercent", "%"),
            Map.entry("storageMaxUsedPercent", "%"),
            Map.entry("batteryPercent", "%"),
            Map.entry("loadPercent", "%"),
            Map.entry("inputVoltage", "V"),
            Map.entry("outputVoltage", "V"),
            Map.entry("runtimeMinutes", "min"),
            Map.entry("totalInterfaceErrors", "count"),
            Map.entry("ifNumber", "count"),
            Map.entry("checkedInterfaces", "count"),
            Map.entry("users", "count"),
            Map.entry("processes", "count"),
            Map.entry("rtspAvailable", "state"),
            Map.entry("httpAvailable", "state"),
            Map.entry("tcpAvailable", "state"),
            Map.entry("pingAvailable", "state")
    );

    private final MetricSampleRepository repository;
    private final MetricThresholdRepository thresholdRepository;
    private final EventService eventService;

    public MetricSampleService(
            MetricSampleRepository repository,
            MetricThresholdRepository thresholdRepository,
            EventService eventService
    ) {
        this.repository = repository;
        this.thresholdRepository = thresholdRepository;
        this.eventService = eventService;
    }

    @Transactional
    public void saveSamples(
            Long componentId,
            String componentName,
            DeviceType componentType,
            String location,
            String source,
            String rawLog,
            Instant sampledAt
    ) {
        Map<String, Double> values = extractMetrics(source, rawLog);
        if (values.isEmpty()) {
            return;
        }

        List<MetricSample> samples = values.entrySet()
                .stream()
                .map(entry -> new MetricSample(
                        componentId,
                        componentName,
                        entry.getKey(),
                        entry.getValue(),
                        UNITS.getOrDefault(entry.getKey(), "value"),
                        source,
                        sampledAt
                ))
                .toList();
        repository.saveAll(samples);
        samples.forEach(sample -> evaluateThresholds(sample, componentType, location));
    }

    @Transactional(readOnly = true)
    public List<MetricSampleResponse> listComponentSamples(Long componentId, int hours) {
        int safeHours = Math.max(1, Math.min(hours, 168));
        Instant from = Instant.now().minus(Duration.ofHours(safeHours));
        List<MetricSample> samples = repository
                .findByComponentIdAndSampledAtGreaterThanEqualOrderBySampledAtAsc(componentId, from);
        if (safeHours <= 24) {
            return samples.stream()
                    .map(MetricSampleResponse::from)
                    .toList();
        }
        return aggregateHourly(samples);
    }

    @Scheduled(cron = "0 15 2 * * *")
    @Transactional
    public void deleteOldSamples() {
        repository.deleteBySampledAtBefore(Instant.now().minus(METRIC_RETENTION));
    }

    @Transactional(readOnly = true)
    public List<MetricThresholdResponse> listThresholds(Long componentId) {
        return thresholdRepository.findByComponentIdOrderByMetricNameAsc(componentId)
                .stream()
                .map(MetricThresholdResponse::from)
                .toList();
    }

    @Transactional
    public MetricThresholdResponse createThreshold(Long componentId, SaveMetricThresholdRequest request) {
        MetricThreshold threshold = new MetricThreshold(
                componentId,
                request.metricName(),
                request.operator(),
                request.warningValue(),
                request.criticalValue(),
                request.enabled() == null || request.enabled()
        );
        return MetricThresholdResponse.from(thresholdRepository.save(threshold));
    }

    @Transactional
    public MetricThresholdResponse updateThreshold(Long thresholdId, SaveMetricThresholdRequest request) {
        MetricThreshold threshold = findThreshold(thresholdId);
        threshold.updateConfiguration(
                request.metricName(),
                request.operator(),
                request.warningValue(),
                request.criticalValue(),
                request.enabled() == null || request.enabled()
        );
        return MetricThresholdResponse.from(threshold);
    }

    @Transactional
    public void deleteThreshold(Long thresholdId) {
        thresholdRepository.delete(findThreshold(thresholdId));
    }

    private MetricThreshold findThreshold(Long thresholdId) {
        return thresholdRepository.findById(thresholdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Metric threshold not found"));
    }

    private List<MetricSampleResponse> aggregateHourly(List<MetricSample> samples) {
        Map<MetricAggregateKey, MetricAggregate> aggregates = new LinkedHashMap<>();
        for (MetricSample sample : samples) {
            MetricAggregateKey key = new MetricAggregateKey(
                    sample.getMetricName(),
                    sample.getUnit(),
                    sample.getSource(),
                    sample.getSampledAt().truncatedTo(ChronoUnit.HOURS)
            );
            aggregates.computeIfAbsent(key, ignored -> new MetricAggregate(sample))
                    .add(sample.getMetricValue());
        }

        return aggregates.values()
                .stream()
                .map(MetricAggregate::toResponse)
                .sorted(Comparator.comparing(MetricSampleResponse::sampledAt)
                        .thenComparing(MetricSampleResponse::metricName))
                .toList();
    }

    private void evaluateThresholds(MetricSample sample, DeviceType componentType, String location) {
        List<MetricThreshold> thresholds = thresholdRepository.findByComponentIdAndMetricNameAndEnabledTrue(
                sample.getComponentId(),
                sample.getMetricName()
        );
        for (MetricThreshold threshold : thresholds) {
            ThresholdState nextState = evaluate(threshold, sample.getMetricValue());
            ThresholdState previousState = threshold.getLastState();
            if (nextState == previousState) {
                continue;
            }

            threshold.updateState(nextState, sample.getSampledAt());
            eventService.ingest(new EventIngestionRequest(
                    "component-" + sample.getComponentId(),
                    sample.getComponentName(),
                    componentType,
                    location,
                    eventTypeFor(sample.getMetricName(), nextState),
                    severityFor(nextState),
                    messageFor(sample, threshold, nextState),
                    detailsFor(sample, threshold, previousState, nextState),
                    sample.getSampledAt()
            ));
        }
    }

    private ThresholdState evaluate(MetricThreshold threshold, double value) {
        boolean critical = threshold.getOperator() == ThresholdOperator.GREATER_THAN
                ? value >= threshold.getCriticalValue()
                : value <= threshold.getCriticalValue();
        if (critical) {
            return ThresholdState.CRITICAL;
        }

        boolean warning = threshold.getOperator() == ThresholdOperator.GREATER_THAN
                ? value >= threshold.getWarningValue()
                : value <= threshold.getWarningValue();
        return warning ? ThresholdState.WARNING : ThresholdState.NORMAL;
    }

    private String eventTypeFor(String metricName, ThresholdState state) {
        if (state == ThresholdState.NORMAL) {
            return "METRIC_THRESHOLD_RECOVERED";
        }
        return "METRIC_THRESHOLD_" + state + "_" + metricNameToEventToken(metricName);
    }

    private String metricNameToEventToken(String metricName) {
        return metricName.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    private Severity severityFor(ThresholdState state) {
        return switch (state) {
            case CRITICAL -> Severity.CRITICAL;
            case WARNING -> Severity.WARNING;
            case NORMAL -> Severity.INFO;
        };
    }

    private String messageFor(MetricSample sample, MetricThreshold threshold, ThresholdState state) {
        if (state == ThresholdState.NORMAL) {
            return sample.getComponentName() + " metric " + sample.getMetricName() + " recovered to normal";
        }
        return sample.getComponentName() + " metric " + sample.getMetricName()
                + " crossed " + state + " threshold";
    }

    private String detailsFor(
            MetricSample sample,
            MetricThreshold threshold,
            ThresholdState previousState,
            ThresholdState nextState
    ) {
        return "metric=" + sample.getMetricName()
                + ", value=" + sample.getMetricValue()
                + ", unit=" + sample.getUnit()
                + ", operator=" + threshold.getOperator()
                + ", warningValue=" + threshold.getWarningValue()
                + ", criticalValue=" + threshold.getCriticalValue()
                + ", previousState=" + previousState
                + ", currentState=" + nextState
                + ", source=" + sample.getSource();
    }

    private Map<String, Double> extractMetrics(String source, String rawLog) {
        Map<String, Double> values = new LinkedHashMap<>();
        addAvailabilityMetric(values, source, rawLog);

        Matcher matcher = NUMERIC_KEY_VALUE.matcher(rawLog);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!UNITS.containsKey(key)) {
                continue;
            }
            values.put(key, Double.parseDouble(matcher.group(2)));
        }

        extractStorageMetrics(values, rawLog);
        return values;
    }

    private void addAvailabilityMetric(Map<String, Double> values, String source, String rawLog) {
        double available = rawLog.toLowerCase().contains("failed") ? 0 : 1;
        switch (source) {
            case "PING" -> values.put("pingAvailable", available);
            case "HTTP_HEALTH" -> values.put("httpAvailable", available);
            case "TCP_PORT" -> values.put("tcpAvailable", available);
            case "RTSP_HEALTH" -> values.put("rtspAvailable", available);
            default -> {
            }
        }
    }

    private void extractStorageMetrics(Map<String, Double> values, String rawLog) {
        Matcher matcher = Pattern.compile("([^;\\[]+) used=(\\d+)%").matcher(rawLog);
        List<Double> storageValues = new ArrayList<>();
        while (matcher.find()) {
            storageValues.add(Double.parseDouble(matcher.group(2)));
        }
        if (!storageValues.isEmpty()) {
            double max = storageValues.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            values.put("storageMaxUsedPercent", max);
        }
    }

    private record MetricAggregateKey(String metricName, String unit, String source, Instant sampledAt) {
    }

    private static class MetricAggregate {
        private final Long componentId;
        private final String componentName;
        private final String metricName;
        private final String unit;
        private final String source;
        private final Instant sampledAt;
        private double total;
        private int count;

        MetricAggregate(MetricSample sample) {
            this.componentId = sample.getComponentId();
            this.componentName = sample.getComponentName();
            this.metricName = sample.getMetricName();
            this.unit = sample.getUnit();
            this.source = sample.getSource();
            this.sampledAt = sample.getSampledAt().truncatedTo(ChronoUnit.HOURS);
        }

        void add(double value) {
            total += value;
            count += 1;
        }

        MetricSampleResponse toResponse() {
            return new MetricSampleResponse(
                    null,
                    componentId,
                    componentName,
                    metricName,
                    total / count,
                    unit,
                    source,
                    sampledAt
            );
        }
    }
}
