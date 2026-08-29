package com.aiops.backend.ai;

import com.aiops.backend.component.MonitoredComponent;
import com.aiops.backend.component.MonitoredComponentRepository;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentComponentBinder;
import com.aiops.backend.incident.IncidentComponentMatchResponse;
import com.aiops.backend.incident.IncidentCorrelationPolicy;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.metric.MetricSample;
import com.aiops.backend.metric.MetricSampleRepository;
import com.aiops.backend.netflow.NetFlowSourceRepository;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import com.aiops.backend.pcap.PacketCaptureAnalysis;
import com.aiops.backend.pcap.PacketCaptureAnalysisRepository;
import com.aiops.backend.report.DiagnosticReport;
import com.aiops.backend.report.DiagnosticReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiContextBuilderService {

    private static final Pattern COMPONENT_DEVICE_ID = Pattern.compile("^component-(\\d+)$");

    private final IncidentRepository incidentRepository;
    private final IncidentCorrelationPolicy correlationPolicy;
    private final MonitoredComponentRepository componentRepository;
    private final MetricSampleRepository metricSampleRepository;
    private final PacketCaptureAnalysisRepository packetCaptureAnalysisRepository;
    private final NetworkFlowRepository networkFlowRepository;
    private final NetFlowSourceRepository netFlowSourceRepository;
    private final DiagnosticReportRepository reportRepository;
    private final IncidentComponentBinder componentBinder;

    public AiContextBuilderService(
            IncidentRepository incidentRepository,
            IncidentCorrelationPolicy correlationPolicy,
            MonitoredComponentRepository componentRepository,
            MetricSampleRepository metricSampleRepository,
            PacketCaptureAnalysisRepository packetCaptureAnalysisRepository,
            NetworkFlowRepository networkFlowRepository,
            NetFlowSourceRepository netFlowSourceRepository,
            DiagnosticReportRepository reportRepository,
            IncidentComponentBinder componentBinder
    ) {
        this.incidentRepository = incidentRepository;
        this.correlationPolicy = correlationPolicy;
        this.componentRepository = componentRepository;
        this.metricSampleRepository = metricSampleRepository;
        this.packetCaptureAnalysisRepository = packetCaptureAnalysisRepository;
        this.networkFlowRepository = networkFlowRepository;
        this.netFlowSourceRepository = netFlowSourceRepository;
        this.reportRepository = reportRepository;
        this.componentBinder = componentBinder;
    }

    @Transactional(readOnly = true)
    public AiIncidentContextResponse build(Long incidentId) {
        Incident incident = incidentRepository.findWithEventsById(incidentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));

        List<AiIncidentContextResponse.RelatedEventSnapshot> relatedEvents = incident.getEvents()
                .stream()
                .sorted(Comparator.comparing(event -> event.getOccurredAt(), Comparator.reverseOrder()))
                .map(event -> new AiIncidentContextResponse.RelatedEventSnapshot(
                        event.getId(),
                        event.getEventType(),
                        event.getSeverity(),
                        event.getMessage(),
                        event.getDetails(),
                        event.getRawLog(),
                        event.getSourceIp(),
                        event.getSyslogSourceName(),
                        event.getParsingProfile(),
                        event.getEventSource(),
                        extractDetailValue(event.getDetails(), "protocol"),
                        extractDetailValue(event.getDetails(), "sourceAddress"),
                        extractDetailInteger(event.getDetails(), "sourcePort"),
                        extractDetailValue(event.getDetails(), "destinationAddress"),
                        extractDetailInteger(event.getDetails(), "destinationPort"),
                        event.getDeviceId(),
                        event.getDeviceName(),
                        event.getDeviceType(),
                        event.getLocation(),
                        event.getOccurredAt()
                ))
                .toList();

        IncidentComponentMatchResponse targetMatch = selectTarget(componentBinder.relatedComponents(incident));
        MonitoredComponent component = targetMatch == null
                ? findLinkedComponent(incident, relatedEvents).orElse(null)
                : componentRepository.findById(targetMatch.id()).orElse(null);

        List<AiIncidentContextResponse.MetricSnapshot> recentMetrics = component == null
                ? List.of()
                : metricSampleRepository
                .findByComponentIdAndSampledAtGreaterThanEqualOrderBySampledAtAsc(
                        component.getId(),
                        Instant.now().minus(Duration.ofHours(24))
                )
                .stream()
                .map(sample -> new AiIncidentContextResponse.MetricSnapshot(
                        sample.getMetricName(),
                        sample.getMetricValue(),
                        sample.getUnit(),
                        sample.getSource(),
                        sample.getSampledAt()
                ))
                .toList();

        List<AiIncidentContextResponse.SimilarIncidentSnapshot> previousSimilarIncidents =
                buildPreviousSimilarIncidents(incident);
        List<AiIncidentContextResponse.PacketCaptureSummarySnapshot> packetCaptureSummaries =
                packetCaptureAnalysisRepository.findTop5ByIncidentIdOrderByCreatedAtDesc(incident.getId())
                        .stream()
                        .map(this::toPacketCaptureSummary)
                        .toList();
        List<AiIncidentContextResponse.NetworkFlowSummarySnapshot> networkFlowSummaries =
                buildNetworkFlowSummaries(incident.getId());
        List<AiIncidentContextResponse.PreviousReportSnapshot> previousReports =
                reportRepository.findTop5ByDeviceIdOrderByGeneratedAtDesc(incident.getDeviceId())
                        .stream()
                        .map(this::toPreviousReportSnapshot)
                        .toList();

        return new AiIncidentContextResponse(
                incident.getId(),
                incident.getCorrelationKey(),
                incident.getTitle(),
                incident.getCategory(),
                incident.getSeverity(),
                incident.getStatus() == null ? null : incident.getStatus().forApi(),
                incident.getLocation(),
                incident.getDeviceId(),
                incident.getDeviceName(),
                incident.getDeviceType(),
                incident.getFirstSeenAt(),
                incident.getLastSeenAt(),
                incident.getCreatedAt(),
                incident.getLastActivityAt(),
                incident.getResolvedAt(),
                incident.durationMinutes(),
                incident.getEventCount(),
                incident.isAcknowledged() || incident.getStatus() == com.aiops.backend.incident.IncidentStatus.ACKNOWLEDGED,
                incident.getPreviousSimilarIncidentId(),
                component == null ? null : new AiIncidentContextResponse.ComponentSnapshot(
                        component.getId(),
                        component.getName(),
                        component.getType(),
                        component.getIpAddress(),
                        component.getHttpUrl(),
                        component.getTcpPort(),
                        component.getLocation(),
                        component.getCriticality(),
                        component.getMonitoringMethods(),
                        component.getLastStatus(),
                        component.getLastError(),
                        component.getLastCheckDetails(),
                        targetMatch == null ? component.getIpAddress() : targetMatch.matchedIp(),
                        targetMatch == null ? null : targetMatch.matchedInterfaceName()
                ),
                relatedEvents,
                recentMetrics,
                packetCaptureSummaries,
                networkFlowSummaries,
                previousSimilarIncidents,
                previousReports
        );
    }

    private Optional<MonitoredComponent> findLinkedComponent(
            Incident incident,
            List<AiIncidentContextResponse.RelatedEventSnapshot> relatedEvents
    ) {
        Optional<Long> componentId = extractComponentId(incident.getDeviceId());
        if (componentId.isEmpty()) {
            componentId = relatedEvents.stream()
                    .map(AiIncidentContextResponse.RelatedEventSnapshot::deviceId)
                    .map(this::extractComponentId)
                    .flatMap(Optional::stream)
                    .findFirst();
        }
        if (componentId.isPresent()) {
            return componentRepository.findById(componentId.get());
        }

        return componentRepository.findByNameIgnoreCase(incident.getDeviceName());
    }

    private IncidentComponentMatchResponse selectTarget(List<IncidentComponentMatchResponse> related) {
        return related.stream()
                .filter(match -> "TARGET".equals(match.relation()))
                .findFirst()
                .orElse(related.isEmpty() ? null : related.getFirst());
    }

    private List<AiIncidentContextResponse.SimilarIncidentSnapshot> buildPreviousSimilarIncidents(Incident incident) {
        Map<Long, Incident> similar = new LinkedHashMap<>();
        String identity = IncidentCorrelationPolicy.identityOf(incident.getCorrelationKey());

        correlationPolicy.family(identity).stream()
                .filter(candidate -> !candidate.getId().equals(incident.getId()))
                .forEach(candidate -> similar.put(candidate.getId(), candidate));

        if (incident.getPreviousSimilarIncidentId() != null) {
            incidentRepository.findById(incident.getPreviousSimilarIncidentId())
                    .ifPresent(candidate -> similar.put(candidate.getId(), candidate));
        }

        if (similar.size() < 5) {
            incidentRepository.findTop5ByDeviceIdAndCategoryAndIdNotOrderByLastSeenAtDesc(
                            incident.getDeviceId(),
                            incident.getCategory(),
                            incident.getId()
                    )
                    .forEach(candidate -> similar.putIfAbsent(candidate.getId(), candidate));
        }

        return similar.values()
                .stream()
                .sorted(Comparator.comparing(Incident::getLastSeenAt).reversed())
                .limit(5)
                .map(candidate -> new AiIncidentContextResponse.SimilarIncidentSnapshot(
                        candidate.getId(),
                        candidate.getCorrelationKey(),
                        candidate.getTitle(),
                        candidate.getCategory(),
                        candidate.getSeverity(),
                        candidate.getStatus() == null ? null : candidate.getStatus().forApi(),
                        candidate.getDeviceId(),
                        candidate.getDeviceName(),
                        candidate.getFirstSeenAt(),
                        candidate.getLastSeenAt(),
                        candidate.getLastActivityAt(),
                        candidate.getResolvedAt(),
                        candidate.getEventCount()
                ))
                .toList();
    }

    private AiIncidentContextResponse.PreviousReportSnapshot toPreviousReportSnapshot(DiagnosticReport report) {
        return new AiIncidentContextResponse.PreviousReportSnapshot(
                report.getId(),
                report.getEventId(),
                report.getDeviceId(),
                report.getDeviceName(),
                report.getEventType(),
                report.getSeverity(),
                report.getSummary(),
                report.getProvider(),
                report.getModel(),
                report.getGeneratedAt()
        );
    }

    private AiIncidentContextResponse.PacketCaptureSummarySnapshot toPacketCaptureSummary(PacketCaptureAnalysis analysis) {
        return new AiIncidentContextResponse.PacketCaptureSummarySnapshot(
                analysis.getId(),
                analysis.getFileName(),
                analysis.getTotalPackets(),
                analysis.getTotalBytes(),
                readList(analysis.getTopSourceIpsText()),
                readList(analysis.getTopDestinationIpsText()),
                readList(analysis.getTopProtocolsText()),
                readList(analysis.getTopDestinationPortsText()),
                readList(analysis.getSuspiciousFindingsText()),
                analysis.getSummary(),
                analysis.getCreatedAt()
        );
    }

    private List<AiIncidentContextResponse.NetworkFlowSummarySnapshot> buildNetworkFlowSummaries(Long incidentId) {
        Map<String, List<NetworkFlow>> grouped = new LinkedHashMap<>();
        for (NetworkFlow flow : networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(incidentId)) {
            String key = String.join("|",
                    value(flow.getSourceId()),
                    value(flow.getSourceIp()),
                    value(flow.getDestinationIp()),
                    value(flow.getAnomalyType() == null ? null : flow.getAnomalyType().name())
            );
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(flow);
        }

        return grouped.values().stream()
                .map(group -> {
                    NetworkFlow first = group.getFirst();
                    String sourceName = first.getSourceId() == null
                            ? "Demo NetFlow source"
                            : netFlowSourceRepository.findById(first.getSourceId())
                            .map(source -> source.getName())
                            .orElse("nfdump collector");
                    Set<Integer> destinationPorts = group.stream()
                            .map(NetworkFlow::getDestinationPort)
                            .filter(port -> port != null)
                            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
                    Set<String> protocols = group.stream()
                            .map(NetworkFlow::getProtocol)
                            .filter(protocol -> protocol != null && !protocol.isBlank())
                            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
                    long totalPackets = group.stream().mapToLong(NetworkFlow::getPackets).sum();
                    long totalBytes = group.stream().mapToLong(NetworkFlow::getBytes).sum();
                    Instant firstSeenAt = group.stream().map(NetworkFlow::getStartTime).min(Instant::compareTo).orElse(first.getStartTime());
                    Instant lastSeenAt = group.stream().map(NetworkFlow::getEndTime).max(Instant::compareTo).orElse(first.getEndTime());
                    return new AiIncidentContextResponse.NetworkFlowSummarySnapshot(
                            sourceName,
                            first.getSourceIp(),
                            first.getDestinationIp(),
                            destinationPorts.stream().toList(),
                            protocols.stream().toList(),
                            group.size(),
                            totalPackets,
                            totalBytes,
                            first.getAnomalyType(),
                            first.getAnomalyReason(),
                            firstSeenAt,
                            lastSeenAt
                    );
                })
                .toList();
    }

    private Optional<Long> extractComponentId(String deviceId) {
        if (deviceId == null) {
            return Optional.empty();
        }
        Matcher matcher = COMPONENT_DEVICE_ID.matcher(deviceId.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(matcher.group(1)));
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String extractDetailValue(String details, String key) {
        if (details == null || details.isBlank()) {
            return null;
        }
        String marker = key + "=";
        int start = details.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int end = details.indexOf(';', valueStart);
        return (end < 0 ? details.substring(valueStart) : details.substring(valueStart, end)).trim();
    }

    private Integer extractDetailInteger(String details, String key) {
        String value = extractDetailValue(details, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<String> readList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
