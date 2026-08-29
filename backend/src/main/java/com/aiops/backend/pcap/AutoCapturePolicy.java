package com.aiops.backend.pcap;

import com.aiops.backend.event.Event;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentStatus;
import com.aiops.backend.netflow.NetFlowAnomalyType;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AutoCapturePolicy {

    private static final Pattern ANOMALY_TYPE = Pattern.compile("(?:^|;\\s*)anomalyType=([^;]+)", Pattern.CASE_INSENSITIVE);

    private final PcapCaptureProperties properties;
    private final NetworkFlowRepository networkFlowRepository;

    public AutoCapturePolicy(PcapCaptureProperties properties) {
        this(properties, null);
    }

    @Autowired
    public AutoCapturePolicy(PcapCaptureProperties properties, NetworkFlowRepository networkFlowRepository) {
        this.properties = properties;
        this.networkFlowRepository = networkFlowRepository;
    }

    public boolean shouldCapture(Incident incident) {
        if (incident == null || !properties.getAuto().isEnabled()) {
            return false;
        }
        IncidentStatus status = incident.getStatus();
        if (status == null || !status.isOpen()) {
            return false;
        }
        if (!categoryAllowed(incident.getCategory())) {
            return false;
        }
        if (!severityAllowed(incident.getSeverity())) {
            return false;
        }
        return matchesSignal(incident);
    }

    public CaptureTrigger triggerType() {
        return properties.getRolling().isEnabled() ? CaptureTrigger.AUTO_ROLLING : CaptureTrigger.AUTO_INCIDENT;
    }

    public int postTriggerSeconds() {
        if (properties.getRolling().isEnabled()) {
            return properties.getRolling().getPostTriggerSeconds();
        }
        return properties.getAuto().getDurationSeconds();
    }

    public int preTriggerSeconds() {
        return properties.getRolling().isEnabled() ? properties.getRolling().getPreTriggerSeconds() : 0;
    }

    private boolean categoryAllowed(String category) {
        Set<String> allowed = csv(properties.getAuto().getCategories());
        return category != null && allowed.contains(category.trim().toUpperCase(Locale.ROOT));
    }

    private boolean severityAllowed(Severity severity) {
        if (severity == null) {
            return false;
        }
        return rank(severity) >= rank(parseSeverity(properties.getAuto().getMinSeverity()));
    }

    private boolean matchesSignal(Incident incident) {
        Set<String> tokens = csv(properties.getAuto().getSignalTokens());
        if (tokens.isEmpty()) {
            return true;
        }
        if (matchesStructuredEvidence(incident, tokens)) {
            return true;
        }
        return matchesFallbackText(incident, tokens);
    }

    private boolean matchesStructuredEvidence(Incident incident, Set<String> tokens) {
        if (incident.getEvents() != null) {
            for (Event event : incident.getEvents()) {
                if (containsToken(event.getEventType(), tokens)) {
                    return true;
                }
                String anomaly = extractAnomalyType(event.getDetails());
                if (containsToken(anomaly, tokens)) {
                    return true;
                }
            }
        }
        if (incident.getId() != null && networkFlowRepository != null) {
            List<NetworkFlow> flows = networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(incident.getId());
            for (NetworkFlow flow : flows) {
                NetFlowAnomalyType anomalyType = flow.getAnomalyType();
                if (anomalyType != null && containsToken(anomalyType.name(), tokens)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesFallbackText(Incident incident, Set<String> tokens) {
        StringBuilder haystack = new StringBuilder();
        append(haystack, incident.getTitle());
        append(haystack, incident.getCategory());
        append(haystack, incident.getCorrelationKey());
        if (incident.getEvents() != null) {
            for (Event event : incident.getEvents()) {
                append(haystack, event.getMessage());
                append(haystack, event.getDetails());
                append(haystack, event.getRawLog());
            }
        }
        return containsToken(haystack.toString(), tokens);
    }

    private boolean containsToken(String value, Set<String> tokens) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        for (String token : tokens) {
            if (normalized.contains(token) || normalized.contains(token.replace('_', ' '))) {
                return true;
            }
        }
        return false;
    }

    private String extractAnomalyType(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        Matcher matcher = ANOMALY_TYPE.matcher(details);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim();
    }

    private void append(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(' ').append(value);
        }
    }

    private Set<String> csv(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(item -> item.trim().toUpperCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toSet());
    }

    private Severity parseSeverity(String raw) {
        if (raw == null || raw.isBlank()) {
            return Severity.WARNING;
        }
        try {
            return Severity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Severity.WARNING;
        }
    }

    private int rank(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 3;
            case WARNING -> 2;
            case INFO -> 1;
        };
    }
}
