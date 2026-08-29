package com.aiops.backend.incident;

import com.aiops.backend.component.ComponentIdentityResolver;
import com.aiops.backend.component.ComponentIdentityResolver.ResolvedComponentIp;
import com.aiops.backend.component.MonitoredComponent;
import com.aiops.backend.component.MonitoredComponentRepository;
import com.aiops.backend.component.NetworkInterfaceRole;
import com.aiops.backend.event.Event;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import com.aiops.backend.pcap.PacketCaptureAnalysis;
import com.aiops.backend.pcap.PacketCaptureAnalysisRepository;
import com.aiops.backend.topology.TopologyIpExtractor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IncidentComponentBinder {

    private static final Pattern COMPONENT_DEVICE_ID = Pattern.compile("^component-(\\d+)$");

    private final ComponentIdentityResolver identityResolver;
    private final NetworkFlowRepository networkFlowRepository;
    private final PacketCaptureAnalysisRepository packetCaptureAnalysisRepository;
    private final MonitoredComponentRepository componentRepository;

    public IncidentComponentBinder(
            ComponentIdentityResolver identityResolver,
            NetworkFlowRepository networkFlowRepository,
            PacketCaptureAnalysisRepository packetCaptureAnalysisRepository,
            MonitoredComponentRepository componentRepository
    ) {
        this.identityResolver = identityResolver;
        this.networkFlowRepository = networkFlowRepository;
        this.packetCaptureAnalysisRepository = packetCaptureAnalysisRepository;
        this.componentRepository = componentRepository;
    }

    public TrafficEndpoints trafficEndpoints(Incident incident) {
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
        return new TrafficEndpoints(sourceIp, destinationIp);
    }

    @Transactional(readOnly = true)
    public List<IncidentComponentMatchResponse> relatedComponents(Incident incident) {
        TrafficEndpoints endpoints = trafficEndpoints(incident);
        String sourceIp = endpoints.sourceIp();
        String destinationIp = endpoints.destinationIp();

        Map<Long, IncidentComponentMatchResponse> matches = new LinkedHashMap<>();
        identityResolver.resolveByIp(destinationIp).ifPresent(resolved ->
                matches.put(resolved.component().getId(), toMatch(resolved, "TARGET")));
        identityResolver.resolveByIp(sourceIp).ifPresent(resolved ->
                matches.putIfAbsent(resolved.component().getId(), toMatch(resolved, "SOURCE")));

        associatedComponent(incident).ifPresent(component -> {
            if (!matches.containsKey(component.getId())) {
                matches.put(component.getId(), toMatch(
                        new ResolvedComponentIp(
                                component,
                                component.getIpAddress(),
                                null,
                                NetworkInterfaceRole.MANAGEMENT,
                                true
                        ),
                        "ASSOCIATED"
                ));
            }
        });
        return new ArrayList<>(matches.values());
    }

    public record TrafficEndpoints(String sourceIp, String destinationIp) {
    }

    private Optional<MonitoredComponent> associatedComponent(Incident incident) {
        Matcher matcher = COMPONENT_DEVICE_ID.matcher(incident.getDeviceId() == null ? "" : incident.getDeviceId());
        if (matcher.matches()) {
            return componentRepository.findById(Long.valueOf(matcher.group(1)));
        }
        if (incident.getDeviceName() != null && !incident.getDeviceName().isBlank()) {
            return componentRepository.findByNameIgnoreCase(incident.getDeviceName());
        }
        return Optional.empty();
    }

    private IncidentComponentMatchResponse toMatch(ResolvedComponentIp resolved, String relation) {
        MonitoredComponent component = resolved.component();
        return new IncidentComponentMatchResponse(
                component.getId(),
                component.getName(),
                component.getType(),
                component.getLastStatus(),
                component.isEnabled(),
                component.getIpAddress(),
                resolved.matchedIp(),
                resolved.matchedInterfaceName(),
                resolved.matchedRole(),
                resolved.matchedPrimary(),
                relation,
                component.getLocation(),
                component.getLastCheckedAt(),
                component.getLastSeenAt(),
                component.getLastError()
        );
    }
}
