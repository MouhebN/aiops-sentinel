package com.aiops.backend.topology;

import com.aiops.backend.component.ComponentIdentityResolver;
import com.aiops.backend.component.MonitoredComponent;
import com.aiops.backend.component.MonitoredComponentRepository;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentCorrelationPolicy;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.netflow.NetFlowAnomalyType;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import com.aiops.backend.pcap.PacketCaptureAnalysis;
import com.aiops.backend.pcap.PacketCaptureAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TopologyService {

    private static final Pattern COMPONENT_DEVICE_ID = Pattern.compile("^component-(\\d+)$");

    private final MonitoredComponentRepository componentRepository;
    private final ComponentRelationRepository relationRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentCorrelationPolicy correlationPolicy;
    private final NetworkFlowRepository networkFlowRepository;
    private final PacketCaptureAnalysisRepository packetCaptureAnalysisRepository;
    private final ComponentIdentityResolver identityResolver;

    public TopologyService(
            MonitoredComponentRepository componentRepository,
            ComponentRelationRepository relationRepository,
            IncidentRepository incidentRepository,
            IncidentCorrelationPolicy correlationPolicy,
            NetworkFlowRepository networkFlowRepository,
            PacketCaptureAnalysisRepository packetCaptureAnalysisRepository,
            ComponentIdentityResolver identityResolver
    ) {
        this.componentRepository = componentRepository;
        this.relationRepository = relationRepository;
        this.incidentRepository = incidentRepository;
        this.correlationPolicy = correlationPolicy;
        this.networkFlowRepository = networkFlowRepository;
        this.packetCaptureAnalysisRepository = packetCaptureAnalysisRepository;
        this.identityResolver = identityResolver;
    }

    @Transactional(readOnly = true)
    public TopologyResponse getTopology() {
        List<MonitoredComponent> components = componentRepository.findAllByOrderByCreatedAtDesc();
        List<ComponentRelation> relations = relationRepository.findAllByOrderByCreatedAtAsc();
        Instant now = Instant.now();
        List<Incident> activeSecurity = incidentRepository.findAll().stream()
                .filter(incident -> incident.getStatus() != null && incident.getStatus().isOpen())
                .filter(incident -> correlationPolicy.isWithinInactivityWindow(incident, now))
                .filter(incident -> "SECURITY".equalsIgnoreCase(incident.getCategory()))
                .toList();

        Map<Long, Set<Long>> incidentIdsByComponent = new HashMap<>();
        Map<Long, TopologySecurityState> securityByComponent = new HashMap<>();
        Map<Long, Set<String>> evidenceByComponent = new HashMap<>();
        Map<Long, Severity> severityByComponent = new HashMap<>();
        Map<String, TopologyExternalEntityResponse> externals = new LinkedHashMap<>();
        List<TopologyActiveAttackResponse> attacks = new ArrayList<>();

        for (Incident incident : activeSecurity) {
            AttackEndpoints endpoints = endpointsOf(incident);
            List<String> evidence = evidenceOf(incident);
            boolean blocked = isBlocked(incident);
            String attackType = attackTypeOf(incident);
            Long targetId = endpoints.destinationComponent == null ? null : endpoints.destinationComponent.getId();
            Long associatedId = associatedComponentId(incident, components);

            if (targetId != null) {
                TopologySecurityState state = isUnderAttack(incident, attackType)
                        ? TopologySecurityState.UNDER_ATTACK
                        : TopologySecurityState.TARGETED;
                mergeState(securityByComponent, targetId, state);
                incidentIdsByComponent.computeIfAbsent(targetId, key -> new LinkedHashSet<>()).add(incident.getId());
                evidenceByComponent.computeIfAbsent(targetId, key -> new LinkedHashSet<>()).addAll(evidence);
                mergeSeverity(severityByComponent, targetId, incident.getSeverity());
            }

            if (associatedId != null && !associatedId.equals(targetId)) {
                mergeState(securityByComponent, associatedId, TopologySecurityState.SUSPICIOUS_ACTIVITY);
                incidentIdsByComponent.computeIfAbsent(associatedId, key -> new LinkedHashSet<>()).add(incident.getId());
                evidenceByComponent.computeIfAbsent(associatedId, key -> new LinkedHashSet<>()).addAll(evidence);
                mergeSeverity(severityByComponent, associatedId, incident.getSeverity());
            }

            if (endpoints.sourceComponent != null && !endpoints.sourceComponent.getId().equals(targetId)) {
                mergeState(securityByComponent, endpoints.sourceComponent.getId(), TopologySecurityState.SUSPICIOUS_ACTIVITY);
                incidentIdsByComponent.computeIfAbsent(endpoints.sourceComponent.getId(), key -> new LinkedHashSet<>())
                        .add(incident.getId());
            }

            if (targetId != null && endpoints.sourceIp != null) {
                String sourceNodeId;
                if (endpoints.sourceComponent != null) {
                    sourceNodeId = componentNodeId(endpoints.sourceComponent.getId());
                } else {
                    sourceNodeId = externalNodeId(endpoints.sourceIp);
                    externals.putIfAbsent(sourceNodeId, new TopologyExternalEntityResponse(
                            sourceNodeId,
                            endpoints.sourceIp,
                            "EXTERNAL",
                            "Suspicious source"
                    ));
                }
                attacks.add(new TopologyActiveAttackResponse(
                        sourceNodeId,
                        componentNodeId(targetId),
                        incident.getId(),
                        incident.getTitle(),
                        incident.getSeverity(),
                        attackType,
                        endpoints.sourceIp,
                        blocked,
                        evidence
                ));
            }
        }

        List<TopologyNodeResponse> nodes = components.stream()
                .map(component -> {
                    Set<Long> incidentIds = incidentIdsByComponent.getOrDefault(component.getId(), Set.of());
                    Set<String> evidence = evidenceByComponent.getOrDefault(component.getId(), Set.of());
                    return new TopologyNodeResponse(
                            componentNodeId(component.getId()),
                            component.getId(),
                            component.getName(),
                            component.getType(),
                            component.getIpAddress(),
                            component.getLocation(),
                            component.getLastStatus(),
                            component.isEnabled(),
                            component.getLastCheckedAt(),
                            component.getLastSeenAt(),
                            incidentIds.size(),
                            severityByComponent.get(component.getId()),
                            securityByComponent.getOrDefault(component.getId(), TopologySecurityState.NORMAL),
                            List.copyOf(incidentIds),
                            List.copyOf(evidence)
                    );
                })
                .toList();

        List<TopologyLinkResponse> links = relations.stream()
                .map(relation -> new TopologyLinkResponse(
                        "relation:" + relation.getId(),
                        componentNodeId(relation.getSource().getId()),
                        componentNodeId(relation.getTarget().getId()),
                        relation.getRelationType(),
                        relation.getLabel()
                ))
                .toList();

        return new TopologyResponse(nodes, links, List.copyOf(externals.values()), attacks);
    }

    private AttackEndpoints endpointsOf(Incident incident) {
        String sourceIp = null;
        String destinationIp = null;
        if (incident.getEvents() != null) {
            for (Event event : incident.getEvents()) {
                if (sourceIp == null) {
                    sourceIp = TopologyIpExtractor.sourceIp(event);
                }
                if (destinationIp == null) {
                    destinationIp = TopologyIpExtractor.destinationIp(event);
                }
            }
        }
        for (NetworkFlow flow : networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(incident.getId())) {
            if (sourceIp == null) {
                sourceIp = TopologyIpExtractor.normalizeIp(flow.getSourceIp());
            }
            if (destinationIp == null) {
                destinationIp = TopologyIpExtractor.normalizeIp(flow.getDestinationIp());
            }
        }
        if (destinationIp == null) {
            destinationIp = TopologyIpExtractor.destinationFromCorrelationKey(incident.getCorrelationKey());
        }
        if (sourceIp == null) {
            sourceIp = TopologyIpExtractor.sourceFromCorrelationKey(incident.getCorrelationKey());
        }
        if (sourceIp == null || destinationIp == null) {
            for (PacketCaptureAnalysis pcap : packetCaptureAnalysisRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId())) {
                if (sourceIp == null) {
                    sourceIp = TopologyIpExtractor.firstIp(pcap.getTopSourceIpsText());
                }
                if (destinationIp == null) {
                    destinationIp = TopologyIpExtractor.firstIp(pcap.getTopDestinationIpsText());
                }
            }
        }
        return new AttackEndpoints(
                TopologyIpExtractor.normalizeIp(sourceIp),
                TopologyIpExtractor.normalizeIp(destinationIp),
                identityResolver.resolveComponent(sourceIp).orElse(null),
                identityResolver.resolveComponent(destinationIp).orElse(null)
        );
    }

    private List<String> evidenceOf(Incident incident) {
        Set<String> evidence = new LinkedHashSet<>();
        if (incident.getEvents() != null) {
            for (Event event : incident.getEvents()) {
                if ("SYSLOG".equalsIgnoreCase(event.getEventSource())) {
                    evidence.add("SYSLOG");
                }
                if ("NETFLOW".equalsIgnoreCase(event.getEventSource())) {
                    evidence.add("NETFLOW");
                }
            }
        }
        if (!networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(incident.getId()).isEmpty()) {
            evidence.add("NETFLOW");
        }
        if (!packetCaptureAnalysisRepository.findByIncidentIdOrderByCreatedAtDesc(incident.getId()).isEmpty()) {
            evidence.add("PCAP");
        }
        return List.copyOf(evidence);
    }

    private boolean isBlocked(Incident incident) {
        if (incident.getEvents() == null) {
            return false;
        }
        return incident.getEvents().stream().anyMatch(event -> {
            String type = event.getEventType() == null ? "" : event.getEventType().toUpperCase(Locale.ROOT);
            String text = ((event.getRawLog() == null ? "" : event.getRawLog()) + " "
                    + (event.getDetails() == null ? "" : event.getDetails()) + " "
                    + (event.getMessage() == null ? "" : event.getMessage()))
                    .toUpperCase(Locale.ROOT);
            return type.contains("DENY") || type.contains("DROP") || type.contains("REJECT")
                    || text.contains(" FIREWALL_DENY") || text.contains("DENY ")
                    || text.contains("DROP ") || text.contains("REJECT ");
        });
    }

    private String attackTypeOf(Incident incident) {
        boolean portScan = incident.getCorrelationKey() != null
                && incident.getCorrelationKey().toUpperCase(Locale.ROOT).contains("PORT_SCAN");
        boolean firewallDeny = false;
        if (incident.getEvents() != null) {
            for (Event event : incident.getEvents()) {
                String type = event.getEventType() == null ? "" : event.getEventType().toUpperCase(Locale.ROOT);
                String text = ((event.getDetails() == null ? "" : event.getDetails()) + " "
                        + (event.getMessage() == null ? "" : event.getMessage()))
                        .toUpperCase(Locale.ROOT);
                if (type.contains("PORT_SCAN") || text.contains("PORT_SCAN")) {
                    portScan = true;
                }
                if (type.contains("FIREWALL_DENY")) {
                    firewallDeny = true;
                }
            }
        }
        for (NetworkFlow flow : networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(incident.getId())) {
            if (flow.getAnomalyType() == NetFlowAnomalyType.PORT_SCAN) {
                portScan = true;
            }
        }
        if (portScan) {
            return "PORT_SCAN";
        }
        if (firewallDeny) {
            return "FIREWALL_DENY";
        }
        return incident.getTitle() == null ? "SECURITY" : incident.getTitle();
    }

    private boolean isUnderAttack(Incident incident, String attackType) {
        if ("PORT_SCAN".equals(attackType)) {
            return true;
        }
        return incident.getSeverity() == Severity.CRITICAL;
    }

    private Long associatedComponentId(Incident incident, List<MonitoredComponent> components) {
        Matcher matcher = COMPONENT_DEVICE_ID.matcher(incident.getDeviceId() == null ? "" : incident.getDeviceId());
        if (matcher.matches()) {
            return Long.valueOf(matcher.group(1));
        }
        if (incident.getDeviceName() != null) {
            for (MonitoredComponent component : components) {
                if (incident.getDeviceName().equalsIgnoreCase(component.getName())) {
                    return component.getId();
                }
            }
        }
        if (incident.getEvents() != null) {
            for (Event event : incident.getEvents()) {
                Matcher eventMatcher = COMPONENT_DEVICE_ID.matcher(event.getDeviceId() == null ? "" : event.getDeviceId());
                if (eventMatcher.matches()) {
                    return Long.valueOf(eventMatcher.group(1));
                }
                if (event.getDeviceName() != null) {
                    for (MonitoredComponent component : components) {
                        if (event.getDeviceName().equalsIgnoreCase(component.getName())) {
                            return component.getId();
                        }
                    }
                }
            }
        }
        return null;
    }

    private void mergeState(Map<Long, TopologySecurityState> states, Long componentId, TopologySecurityState next) {
        TopologySecurityState current = states.get(componentId);
        if (current == null || rank(next) > rank(current)) {
            states.put(componentId, next);
        }
    }

    private void mergeSeverity(Map<Long, Severity> severities, Long componentId, Severity next) {
        Severity current = severities.get(componentId);
        if (current == null || rank(next) > rank(current)) {
            severities.put(componentId, next);
        }
    }

    private int rank(TopologySecurityState state) {
        return switch (state) {
            case NORMAL -> 0;
            case SUSPICIOUS_ACTIVITY -> 1;
            case TARGETED -> 2;
            case UNDER_ATTACK -> 3;
        };
    }

    private int rank(Severity severity) {
        return switch (severity) {
            case INFO -> 1;
            case WARNING -> 2;
            case CRITICAL -> 3;
        };
    }

    private String componentNodeId(Long id) {
        return "component:" + id;
    }

    private String externalNodeId(String ip) {
        return "external:" + ip;
    }

    private record AttackEndpoints(
            String sourceIp,
            String destinationIp,
            MonitoredComponent sourceComponent,
            MonitoredComponent destinationComponent
    ) {
    }
}
