package com.aiops.backend.netflow;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import com.aiops.backend.device.Device;
import com.aiops.backend.device.DeviceRepository;
import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentCorrelationPolicy;
import com.aiops.backend.incident.IncidentCreatedEvent;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.pcap.IncidentAutoCaptureCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NetFlowAnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(NetFlowAnomalyDetectionService.class);
    private static final Set<Integer> SENSITIVE_PORTS = Set.of(22, 445, 1433, 3306, 3389, 5432);
    private static final Duration WINDOW = Duration.ofMinutes(2);

    private final NetworkFlowRepository networkFlowRepository;
    private final IncidentRepository incidentRepository;
    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final AuditLogService auditLogService;
    private final NetFlowProperties properties;
    private final NetFlowIncidentPersistence incidentPersistence;
    private final IncidentCorrelationPolicy correlationPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final IncidentAutoCaptureCoordinator autoCaptureCoordinator;

    public NetFlowAnomalyDetectionService(
            NetworkFlowRepository networkFlowRepository,
            IncidentRepository incidentRepository,
            EventRepository eventRepository,
            DeviceRepository deviceRepository,
            AuditLogService auditLogService,
            NetFlowProperties properties,
            NetFlowIncidentPersistence incidentPersistence,
            IncidentCorrelationPolicy correlationPolicy,
            ApplicationEventPublisher eventPublisher,
            IncidentAutoCaptureCoordinator autoCaptureCoordinator
    ) {
        this.networkFlowRepository = networkFlowRepository;
        this.incidentRepository = incidentRepository;
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
        this.properties = properties;
        this.incidentPersistence = incidentPersistence;
        this.correlationPolicy = correlationPolicy;
        this.eventPublisher = eventPublisher;
        this.autoCaptureCoordinator = autoCaptureCoordinator;
    }

    @Transactional
    public DetectionResult analyze(NetFlowSource source, List<NetworkFlow> importedFlows) {
        if (importedFlows.isEmpty()) {
            return new DetectionResult(0, 0);
        }

        Map<Long, AnomalyMark> marks = new HashMap<>();
        applyPortScanRule(importedFlows, marks);
        applyManyDestinationsRule(importedFlows, marks);
        applySensitivePortRule(importedFlows, marks);
        applyHighVolumeRule(importedFlows, marks);

        long incidentsCreated = 0;
        Set<Long> touchedIncidentIds = new HashSet<>();
        for (Map.Entry<Long, AnomalyMark> entry : marks.entrySet()) {
            NetworkFlow flow = importedFlows.stream()
                    .filter(candidate -> candidate.getId().equals(entry.getKey()))
                    .findFirst()
                    .orElse(null);
            if (flow == null) {
                continue;
            }
            IncidentLinkResult incidentResult = linkFlowToIncident(source, flow, entry.getValue());
            flow.markSuspicious(entry.getValue().type(), entry.getValue().reason(), incidentResult.incident().getId());
            incidentsCreated += incidentResult.created() ? 1 : 0;
            if (incidentResult.incident().getId() != null) {
                touchedIncidentIds.add(incidentResult.incident().getId());
            }
            if (!incidentResult.skippedDuplicate()) {
                auditLogService.log(
                        AuditAction.NETFLOW_ANOMALY_DETECTED,
                        "NETWORK_FLOW",
                        flow.getId().toString(),
                        entry.getValue().reason()
                );
            }
        }

        networkFlowRepository.saveAll(importedFlows);
        for (Long incidentId : touchedIncidentIds) {
            autoCaptureCoordinator.evaluateAndTrigger(incidentId, "netflow-anomaly-attached");
        }
        log.info(
                "[NetFlow-import] import completed suspiciousFlows={} incidentsCreated={}",
                marks.size(),
                incidentsCreated
        );
        return new DetectionResult(marks.size(), incidentsCreated);
    }

    private void applyPortScanRule(List<NetworkFlow> flows, Map<Long, AnomalyMark> marks) {
        Map<String, List<NetworkFlow>> grouped = new HashMap<>();
        flows.stream()
                .filter(flow -> "TCP".equalsIgnoreCase(flow.getProtocol()))
                .filter(flow -> flow.getDestinationPort() != null)
                .forEach(flow -> grouped.computeIfAbsent(
                        flow.getSourceIp() + "->" + flow.getDestinationIp(),
                        key -> new ArrayList<>()
                ).add(flow));

        grouped.values().forEach(group -> applySlidingWindow(group, window -> {
            Set<Integer> ports = new HashSet<>();
            window.forEach(flow -> {
                if (flow.getDestinationPort() != null) {
                    ports.add(flow.getDestinationPort());
                }
            });
            if (ports.size() >= 5) {
                String sourceIp = window.getFirst().getSourceIp();
                String destinationIp = window.getFirst().getDestinationIp();
                String reason = "Possible port scan: " + sourceIp + " probed " + destinationIp
                        + " on ports " + ports.stream().sorted().map(String::valueOf).toList();
                window.forEach(flow -> mark(
                        marks,
                        flow.getId(),
                        new AnomalyMark(
                                NetFlowAnomalyType.PORT_SCAN,
                                reason,
                                4
                        )
                ));
            }
        }));
    }

    private void applyManyDestinationsRule(List<NetworkFlow> flows, Map<Long, AnomalyMark> marks) {
        Map<String, List<NetworkFlow>> grouped = new HashMap<>();
        flows.forEach(flow -> grouped.computeIfAbsent(flow.getSourceIp(), key -> new ArrayList<>()).add(flow));

        grouped.values().forEach(group -> applySlidingWindow(group, window -> {
            Set<String> destinations = new HashSet<>();
            window.forEach(flow -> destinations.add(flow.getDestinationIp()));
            if (destinations.size() >= 5) {
                String sourceIp = window.getFirst().getSourceIp();
                String reason = "One source contacted many destinations in a short interval: "
                        + sourceIp + " -> " + destinations.stream().sorted().toList();
                window.forEach(flow -> mark(
                        marks,
                        flow.getId(),
                        new AnomalyMark(NetFlowAnomalyType.MANY_DESTINATIONS, reason, 3)
                ));
            }
        }));
    }

    private void applySensitivePortRule(List<NetworkFlow> flows, Map<Long, AnomalyMark> marks) {
        flows.stream()
                .filter(flow -> flow.getDestinationPort() != null)
                .filter(flow -> SENSITIVE_PORTS.contains(flow.getDestinationPort()))
                .forEach(flow -> mark(
                        marks,
                        flow.getId(),
                        new AnomalyMark(
                                NetFlowAnomalyType.SENSITIVE_PORT_ACCESS,
                                "Traffic reached sensitive port " + flow.getDestinationPort()
                                        + " from " + flow.getSourceIp() + " to " + flow.getDestinationIp(),
                                2
                        )
                ));
    }

    private void applyHighVolumeRule(List<NetworkFlow> flows, Map<Long, AnomalyMark> marks) {
        flows.stream()
                .filter(flow -> flow.getBytes() >= properties.getHighVolumeThresholdBytes())
                .forEach(flow -> mark(
                        marks,
                        flow.getId(),
                        new AnomalyMark(
                                NetFlowAnomalyType.HIGH_VOLUME_TRANSFER,
                                "High volume transfer detected: " + flow.getBytes() + " bytes from "
                                        + flow.getSourceIp() + " to " + flow.getDestinationIp(),
                                1
                        )
                ));
    }

    private void applySlidingWindow(List<NetworkFlow> flows, WindowConsumer consumer) {
        List<NetworkFlow> sorted = flows.stream()
                .sorted(Comparator.comparing(NetworkFlow::getStartTime))
                .toList();
        for (int start = 0; start < sorted.size(); start++) {
            List<NetworkFlow> window = new ArrayList<>();
            Instant windowStart = sorted.get(start).getStartTime();
            for (int end = start; end < sorted.size(); end++) {
                NetworkFlow candidate = sorted.get(end);
                if (Duration.between(windowStart, candidate.getStartTime()).compareTo(WINDOW) > 0) {
                    break;
                }
                window.add(candidate);
            }
            consumer.accept(window);
        }
    }

    private void mark(Map<Long, AnomalyMark> marks, Long flowId, AnomalyMark next) {
        AnomalyMark current = marks.get(flowId);
        if (current == null || next.priority() > current.priority()) {
            marks.put(flowId, next);
        }
    }

    private IncidentLinkResult linkFlowToIncident(NetFlowSource source, NetworkFlow flow, AnomalyMark mark) {
        if (flow.getIncidentId() != null) {
            Incident alreadyLinked = incidentRepository.findWithEventsById(flow.getIncidentId()).orElse(null);
            if (alreadyLinked != null) {
                log.info(
                        "[NetFlow-import] skipped duplicate evidence flowId={} incidentId={} correlationKey={}",
                        flow.getId(),
                        alreadyLinked.getId(),
                        alreadyLinked.getCorrelationKey()
                );
                return new IncidentLinkResult(alreadyLinked, false, true);
            }
        }

        Incident syslogIncident = findMatchingSyslogIncident(flow, mark.type());
        if (syslogIncident != null) {
            log.info(
                    "Reused matching Syslog SECURITY incident {} for NetFlow src={} dst={} anomaly={}",
                    syslogIncident.getId(),
                    flow.getSourceIp(),
                    flow.getDestinationIp(),
                    mark.type()
            );
            return attachEvidence(syslogIncident, source, flow, mark, false);
        }

        String baseKey = correlationKey(flow, mark.type());
        Incident correlationMatch = findReusableCorrelationMatch(baseKey, flow, mark.type());
        if (correlationMatch != null) {
            log.info(
                    "[NetFlow-correlation] reused existing NetFlow incident correlationKey={} incidentId={} src={} dst={} anomaly={}",
                    correlationMatch.getCorrelationKey(),
                    correlationMatch.getId(),
                    flow.getSourceIp(),
                    flow.getDestinationIp(),
                    mark.type()
            );
            return attachEvidence(correlationMatch, source, flow, mark, false);
        }

        String uniqueKey = correlationPolicy.nextFreeCorrelationKey(baseKey);
        Incident occupant = incidentRepository.findFirstByCorrelationKeyOrderByLastSeenAtDesc(uniqueKey).orElse(null);
        if (occupant != null && correlationPolicy.canReuse(occupant, flow.getEndTime())
                && matchesIncidentEndpoints(occupant, flow, mark.type())) {
            log.info(
                    "[NetFlow-correlation] reused existing NetFlow incident correlationKey={} incidentId={}",
                    occupant.getCorrelationKey(),
                    occupant.getId()
            );
            return attachEvidence(occupant, source, flow, mark, false);
        }

        Event event = createNetFlowEvent(source, flow, mark);
        try {
            Incident created = incidentPersistence.persistNewIncident(
                    uniqueKey,
                    event,
                    DeviceStatus.WARNING,
                    titleFor(flow, mark.type())
            );
            correlationPolicy.findPreviousSimilar(baseKey, created.getId())
                    .ifPresent(previous -> {
                        created.setPreviousSimilarIncidentId(previous.getId());
                        incidentRepository.save(created);
                    });
            log.info(
                    "Created SECURITY incident {} for NetFlow src={} dst={} anomaly={} key={}",
                    created.getId(),
                    flow.getSourceIp(),
                    flow.getDestinationIp(),
                    mark.type(),
                    uniqueKey
            );
            eventPublisher.publishEvent(new IncidentCreatedEvent(created.getId()));
            return new IncidentLinkResult(reloadIncident(created.getId()), true, false);
        } catch (RuntimeException exception) {
            if (!incidentPersistence.isCorrelationKeyConflict(exception)) {
                throw exception;
            }
            Incident raced = incidentRepository.findFirstByCorrelationKeyOrderByLastSeenAtDesc(uniqueKey)
                    .orElseThrow(() -> exception);
            if (!correlationPolicy.canReuse(raced, flow.getEndTime())) {
                throw exception;
            }
            log.info(
                    "[NetFlow-correlation] reused existing NetFlow incident correlationKey={} incidentId={} after unique constraint",
                    raced.getCorrelationKey(),
                    raced.getId()
            );
            return attachEvidence(raced, source, flow, mark, false);
        }
    }

    private IncidentLinkResult attachEvidence(
            Incident incident,
            NetFlowSource source,
            NetworkFlow flow,
            AnomalyMark mark,
            boolean created
    ) {
        Incident managed = reloadIncident(incident.getId());
        Instant evidenceTime = evidenceTimeOf(flow);
        if (alreadyHasNetFlowEvidence(managed, flow, mark.type())) {
            managed.recordActivity(evidenceTime);
            incidentRepository.save(managed);
            log.info(
                    "[NetFlow-import] skipped duplicate event flowId={} incidentId={} src={} dst={} dpt={} lastActivityAt={}",
                    flow.getId(),
                    managed.getId(),
                    flow.getSourceIp(),
                    flow.getDestinationIp(),
                    flow.getDestinationPort(),
                    managed.getLastActivityAt()
            );
            return new IncidentLinkResult(managed, created, true);
        }
        Event event = eventRepository.save(createNetFlowEvent(source, flow, mark));
        return attachPersistedEvent(managed, event, flow, mark, created);
    }

    private IncidentLinkResult attachPersistedEvent(
            Incident incident,
            Event event,
            NetworkFlow flow,
            AnomalyMark mark,
            boolean created
    ) {
        Incident managed = reloadIncident(incident.getId());
        Instant evidenceTime = event.getOccurredAt() != null ? event.getOccurredAt() : evidenceTimeOf(flow);
        if (alreadyHasNetFlowEvidence(managed, flow, mark.type())) {
            managed.recordActivity(evidenceTime);
            incidentRepository.save(managed);
            log.info(
                    "[NetFlow-import] skipped duplicate event flowId={} incidentId={} src={} dst={} dpt={} lastActivityAt={}",
                    flow.getId(),
                    managed.getId(),
                    flow.getSourceIp(),
                    flow.getDestinationIp(),
                    flow.getDestinationPort(),
                    managed.getLastActivityAt()
            );
            return new IncidentLinkResult(managed, created, true);
        }
        managed.markActive(DeviceStatus.WARNING, titleFor(flow, mark.type()));
        managed.addEvent(event);
        incidentRepository.save(managed);
        return new IncidentLinkResult(managed, created, false);
    }

    private Incident reloadIncident(Long incidentId) {
        return incidentRepository.findWithEventsById(incidentId).orElseThrow();
    }

    private Instant evidenceTimeOf(NetworkFlow flow) {
        if (flow.getEndTime() != null) {
            return flow.getEndTime();
        }
        if (flow.getStartTime() != null) {
            return flow.getStartTime();
        }
        return Instant.now();
    }

    private boolean alreadyHasNetFlowEvidence(Incident incident, NetworkFlow flow, NetFlowAnomalyType anomalyType) {
        if (incident.getEvents() == null || incident.getEvents().isEmpty()) {
            return false;
        }
        return incident.getEvents().stream().anyMatch(event ->
                "NETFLOW".equalsIgnoreCase(event.getEventSource())
                        && matchesFlowAddresses(
                        sourceAddressOf(event),
                        destinationAddressOf(event),
                        flow,
                        anomalyType
                )
                        && sameDestinationPort(event, flow)
        );
    }

    private boolean sameDestinationPort(Event event, NetworkFlow flow) {
        if (flow.getDestinationPort() == null) {
            return true;
        }
        Integer eventPort = destinationPortOf(event);
        return eventPort != null && eventPort.equals(flow.getDestinationPort());
    }

    private Integer destinationPortOf(Event event) {
        String labeled = extractDetailValue(joinEventText(event), "destinationPort");
        if (labeled != null && !labeled.isBlank()) {
            try {
                return Integer.valueOf(labeled.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher matcher = Pattern.compile("(?i)\\bDPT=(\\d+)").matcher(joinEventText(event));
        if (matcher.find()) {
            return Integer.valueOf(matcher.group(1));
        }
        return null;
    }

    private Event createNetFlowEvent(NetFlowSource source, NetworkFlow flow, AnomalyMark mark) {
        String deviceId = "netflow-source-" + (source == null || source.getId() == null ? "demo" : source.getId());
        String deviceName = source == null ? "Demo NetFlow Collector" : source.getName();
        String location = source == null ? "NetFlow demo" : "NetFlow collector";
        Instant occurredAt = evidenceTimeOf(flow);
        Event event = new Event(
                deviceId,
                deviceName,
                DeviceType.FIREWALL,
                location,
                eventTypeFor(mark.type()),
                severityFor(mark.type()),
                messageFor(flow, mark.type()),
                detailsFor(flow, mark),
                flow.getRawRecord(),
                flow.getSourceIp(),
                null,
                null,
                "NETFLOW",
                occurredAt
        );
        deviceRepository.save(
                deviceRepository.findById(deviceId)
                        .map(existing -> {
                            existing.updateFromEvent(deviceName, DeviceType.FIREWALL, location, DeviceStatus.WARNING, occurredAt);
                            return existing;
                        })
                        .orElseGet(() -> new Device(deviceId, deviceName, DeviceType.FIREWALL, location, DeviceStatus.WARNING, occurredAt))
        );
        return event;
    }

    private Incident findMatchingSyslogIncident(NetworkFlow flow, NetFlowAnomalyType anomalyType) {
        Instant flowTime = flow.getEndTime() == null ? Instant.now() : flow.getEndTime();
        Instant threshold = flowTime.minus(correlationPolicy.inactivityWindow());
        List<Incident> candidates = incidentRepository
                .findByCategoryAndLastSeenAtGreaterThanEqualOrderByLastSeenAtDesc("SECURITY", threshold);
        log.info(
                "[NetFlow-correlation] looking for Syslog SECURITY incident src={} dst={} anomaly={} flowEnd={} windowMinutes={} securityCandidates={}",
                flow.getSourceIp(),
                flow.getDestinationIp(),
                anomalyType,
                flow.getEndTime(),
                correlationPolicy.inactivityWindow().toMinutes(),
                candidates.size()
        );
        for (Incident candidate : candidates) {
            String reason = syslogMatchRejectionReason(candidate, flow, anomalyType);
            if (reason == null) {
                log.info(
                        "[NetFlow-correlation] MATCH incidentId={} category={} status={} firstSeen={} lastSeen={} src={} dst={}",
                        candidate.getId(),
                        candidate.getCategory(),
                        candidate.getStatus(),
                        candidate.getFirstSeenAt(),
                        candidate.getLastSeenAt(),
                        sourceAddressOf(firstSyslogEvent(candidate)),
                        destinationAddressOf(firstSyslogEvent(candidate))
                );
                return candidate;
            }
            log.info(
                    "[NetFlow-correlation] REJECT incidentId={} category={} status={} firstSeen={} lastSeen={} src={} dst={} timeCompatible={} reason={}",
                    candidate.getId(),
                    candidate.getCategory(),
                    candidate.getStatus(),
                    candidate.getFirstSeenAt(),
                    candidate.getLastSeenAt(),
                    sourceAddressOf(firstSyslogEvent(candidate)),
                    destinationAddressOf(firstSyslogEvent(candidate)),
                    correlationPolicy.canReuse(candidate, flowTime),
                    reason
            );
        }
        log.info(
                "[NetFlow-correlation] no matching Syslog incident for src={} dst={} anomaly={}",
                flow.getSourceIp(),
                flow.getDestinationIp(),
                anomalyType
        );
        return null;
    }

    private String syslogMatchRejectionReason(Incident candidate, NetworkFlow flow, NetFlowAnomalyType anomalyType) {
        if (candidate == null) {
            return "candidate is null";
        }
        if (!"SECURITY".equalsIgnoreCase(candidate.getCategory())) {
            return "category is " + candidate.getCategory() + ", expected SECURITY";
        }
        Instant flowTime = flow.getEndTime() == null ? Instant.now() : flow.getEndTime();
        if (!correlationPolicy.canReuse(candidate, flowTime)) {
            return "episode not reusable status=" + candidate.getStatus()
                    + " lastActivityAt=" + correlationPolicy.lastActivity(candidate)
                    + " flowEnd=" + flowTime
                    + " window=" + correlationPolicy.inactivityWindow().toMinutes() + "m";
        }
        if (!matchesIncidentEndpoints(candidate, flow, anomalyType)) {
            return "endpoint/anomaly mismatch src=" + sourceAddressOf(firstSyslogEvent(candidate))
                    + " dst=" + destinationAddressOf(firstSyslogEvent(candidate))
                    + " flowSrc=" + flow.getSourceIp()
                    + " flowDst=" + flow.getDestinationIp()
                    + " anomaly=" + anomalyType;
        }
        boolean syslogMatch = candidate.getEvents().stream().anyMatch(event -> matchesSyslogEvent(event, flow, anomalyType));
        if (!syslogMatch) {
            return "no SYSLOG event matched src/dst/anomaly; eventSources="
                    + candidate.getEvents().stream()
                    .map(event -> event.getEventSource() + "/" + event.getEventType())
                    .toList();
        }
        return null;
    }

    private Event firstSyslogEvent(Incident incident) {
        if (incident == null || incident.getEvents() == null || incident.getEvents().isEmpty()) {
            return null;
        }
        return incident.getEvents().stream()
                .filter(event -> "SYSLOG".equalsIgnoreCase(event.getEventSource()))
                .findFirst()
                .orElse(incident.getEvents().iterator().next());
    }

    private boolean isReusableIncident(Incident incident, NetworkFlow flow, NetFlowAnomalyType anomalyType) {
        if (incident == null) {
            return false;
        }
        if (!"SECURITY".equalsIgnoreCase(incident.getCategory())) {
            return false;
        }
        Instant flowTime = flow.getEndTime() == null ? Instant.now() : flow.getEndTime();
        if (!correlationPolicy.canReuse(incident, flowTime)) {
            return false;
        }
        return matchesIncidentEndpoints(incident, flow, anomalyType);
    }

    private boolean matchesIncidentEndpoints(Incident incident, NetworkFlow flow, NetFlowAnomalyType anomalyType) {
        if (incident.getEvents() == null || incident.getEvents().isEmpty()) {
            return false;
        }
        return incident.getEvents().stream().anyMatch(event ->
                matchesFlowAddresses(sourceAddressOf(event), destinationAddressOf(event), flow, anomalyType)
                        && matchesAnomaly(extractDetailValue(event.getDetails(), "anomalyType"), event.getEventType(), anomalyType)
        );
    }

    private boolean matchesSyslogEvent(Event event, NetworkFlow flow, NetFlowAnomalyType anomalyType) {
        if (!"SYSLOG".equalsIgnoreCase(event.getEventSource())) {
            return false;
        }
        return matchesFlowAddresses(sourceAddressOf(event), destinationAddressOf(event), flow, anomalyType)
                && matchesAnomaly(extractDetailValue(event.getDetails(), "anomalyType"), event.getEventType(), anomalyType);
    }

    private boolean matchesFlowAddresses(String sourceAddress, String destinationAddress, NetworkFlow flow, NetFlowAnomalyType anomalyType) {
        if (sourceAddress == null || sourceAddress.isBlank()) {
            return false;
        }
        boolean sourceMatch = sourceAddress.equalsIgnoreCase(flow.getSourceIp());
        if (anomalyType == NetFlowAnomalyType.MANY_DESTINATIONS) {
            return sourceMatch;
        }
        if (destinationAddress == null || destinationAddress.isBlank()) {
            return false;
        }
        return sourceMatch && destinationAddress.equalsIgnoreCase(flow.getDestinationIp());
    }

    private boolean matchesAnomaly(String explicitAnomaly, String eventType, NetFlowAnomalyType anomalyType) {
        if (explicitAnomaly != null && !explicitAnomaly.isBlank()) {
            return explicitAnomaly.equalsIgnoreCase(anomalyType.name());
        }
        if (eventType != null && eventType.toUpperCase(Locale.ROOT).contains("PORT_SCAN")) {
            return anomalyType == NetFlowAnomalyType.PORT_SCAN;
        }
        return true;
    }

    private String sourceAddressOf(Event event) {
        if (event == null) {
            return null;
        }
        String labeled = extractLabeledAddress(event, "sourceAddress", "SRC", "src");
        if (labeled != null) {
            return labeled;
        }
        return event.getSourceIp();
    }

    private String destinationAddressOf(Event event) {
        if (event == null) {
            return null;
        }
        return extractLabeledAddress(event, "destinationAddress", "DST", "dst");
    }

    private String extractLabeledAddress(Event event, String... keys) {
        String text = joinEventText(event);
        for (String key : keys) {
            String structured = extractDetailValue(text, key);
            if (structured != null && !structured.isBlank() && !structured.contains(" ")) {
                return structured;
            }
            Matcher matcher = Pattern.compile("(?i)\\b" + Pattern.quote(key) + "=([^;\\s]+)").matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private String joinEventText(Event event) {
        String details = event.getDetails() == null ? "" : event.getDetails();
        String rawLog = event.getRawLog() == null ? "" : event.getRawLog();
        if (rawLog.isBlank()) {
            return details;
        }
        if (details.isBlank()) {
            return rawLog;
        }
        return details + " " + rawLog;
    }

    private Incident findReusableCorrelationMatch(String baseKey, NetworkFlow flow, NetFlowAnomalyType anomalyType) {
        for (Incident candidate : correlationPolicy.family(baseKey)) {
            if (isReusableIncident(candidate, flow, anomalyType)) {
                return candidate;
            }
        }
        return null;
    }

    private String extractDetailValue(String details, String key) {
        if (details == null || details.isBlank()) {
            return null;
        }
        String marker = key + "=";
        int index = details.indexOf(marker);
        if (index < 0) {
            return null;
        }
        int start = index + marker.length();
        int end = details.indexOf(';', start);
        if (end < 0) {
            end = details.length();
        }
        return details.substring(start, end).trim();
    }

    private String correlationKey(NetworkFlow flow, NetFlowAnomalyType anomalyType) {
        if (anomalyType == NetFlowAnomalyType.MANY_DESTINATIONS) {
            return "NETFLOW:" + anomalyType + ":" + flow.getSourceIp().toLowerCase(Locale.ROOT);
        }
        return "NETFLOW:" + anomalyType + ":" + flow.getSourceIp().toLowerCase(Locale.ROOT)
                + ":" + flow.getDestinationIp().toLowerCase(Locale.ROOT);
    }

    private String titleFor(NetworkFlow flow, NetFlowAnomalyType anomalyType) {
        return switch (anomalyType) {
            case PORT_SCAN -> "Possible port scan from " + flow.getSourceIp() + " to " + flow.getDestinationIp();
            case MANY_DESTINATIONS -> "One source contacted many destinations: " + flow.getSourceIp();
            case SENSITIVE_PORT_ACCESS -> "Sensitive port access from " + flow.getSourceIp() + " to " + flow.getDestinationIp();
            case HIGH_VOLUME_TRANSFER -> "High volume transfer from " + flow.getSourceIp() + " to " + flow.getDestinationIp();
        };
    }

    private String messageFor(NetworkFlow flow, NetFlowAnomalyType anomalyType) {
        return switch (anomalyType) {
            case PORT_SCAN -> "NetFlow detected repeated TCP connections across multiple destination ports.";
            case MANY_DESTINATIONS -> "NetFlow detected one source contacting many destinations in a short window.";
            case SENSITIVE_PORT_ACCESS -> "NetFlow detected access to a sensitive service port.";
            case HIGH_VOLUME_TRANSFER -> "NetFlow detected a high volume data transfer.";
        };
    }

    private String detailsFor(NetworkFlow flow, AnomalyMark mark) {
        StringBuilder details = new StringBuilder()
                .append("sourceAddress=").append(flow.getSourceIp())
                .append("; destinationAddress=").append(flow.getDestinationIp())
                .append("; sourcePort=").append(flow.getSourcePort() == null ? "" : flow.getSourcePort())
                .append("; destinationPort=").append(flow.getDestinationPort() == null ? "" : flow.getDestinationPort())
                .append("; protocol=").append(flow.getProtocol())
                .append("; anomalyType=").append(mark.type().name())
                .append("; packets=").append(flow.getPackets())
                .append("; bytes=").append(flow.getBytes());
        if (mark.reason() != null && !mark.reason().isBlank()) {
            details.append("; reason=").append(mark.reason());
        }
        return details.toString();
    }

    private String eventTypeFor(NetFlowAnomalyType anomalyType) {
        return switch (anomalyType) {
            case PORT_SCAN -> "NETFLOW_PORT_SCAN";
            case MANY_DESTINATIONS -> "NETFLOW_MANY_DESTINATIONS";
            case SENSITIVE_PORT_ACCESS -> "NETFLOW_SENSITIVE_PORT_ACCESS";
            case HIGH_VOLUME_TRANSFER -> "NETFLOW_HIGH_VOLUME_TRANSFER";
        };
    }

    private Severity severityFor(NetFlowAnomalyType anomalyType) {
        return switch (anomalyType) {
            case PORT_SCAN -> Severity.CRITICAL;
            case MANY_DESTINATIONS, SENSITIVE_PORT_ACCESS, HIGH_VOLUME_TRANSFER -> Severity.WARNING;
        };
    }

    public record DetectionResult(long suspiciousFlows, long incidentsCreated) {
    }

    private record AnomalyMark(NetFlowAnomalyType type, String reason, int priority) {
    }

    private record IncidentLinkResult(Incident incident, boolean created, boolean skippedDuplicate) {
    }

    @FunctionalInterface
    private interface WindowConsumer {
        void accept(List<NetworkFlow> flows);
    }
}
