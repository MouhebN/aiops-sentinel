package com.aiops.backend.incident;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Shared episode selection for Syslog/Event and NetFlow correlation.
 * Open incidents (ACTIVE / ACKNOWLEDGED) may receive evidence only while
 * {@code evidenceTime - lastActivityAt} is within the inactivity window.
 * RESOLVED (and legacy RECOVERED) incidents are never reused.
 */
@Component
public class IncidentCorrelationPolicy {

    private final IncidentProperties properties;
    private final IncidentRepository incidentRepository;

    public IncidentCorrelationPolicy(IncidentProperties properties, IncidentRepository incidentRepository) {
        this.properties = properties;
        this.incidentRepository = incidentRepository;
    }

    public Duration inactivityWindow() {
        long minutes = properties.getCorrelationInactivityMinutes();
        if (minutes < 1) {
            minutes = 30L;
        }
        return Duration.ofMinutes(minutes);
    }

    public boolean isOpen(Incident incident) {
        return incident != null && incident.getStatus() != null && incident.getStatus().isOpen();
    }

    public boolean isClosed(Incident incident) {
        return incident != null && incident.getStatus() != null && incident.getStatus().isClosed();
    }

    public Instant lastActivity(Incident incident) {
        if (incident == null) {
            return null;
        }
        if (incident.getLastActivityAt() != null) {
            return incident.getLastActivityAt();
        }
        return incident.getLastSeenAt();
    }

    public boolean isWithinInactivityWindow(Incident incident, Instant evidenceTime) {
        Instant evidence = evidenceTime == null ? Instant.now() : evidenceTime;
        Instant last = lastActivity(incident);
        if (last == null) {
            return false;
        }
        Duration gap = Duration.between(last, evidence);
        if (gap.isNegative()) {
            return true;
        }
        return gap.compareTo(inactivityWindow()) <= 0;
    }

    public boolean canReuse(Incident incident, Instant evidenceTime) {
        return isOpen(incident) && isWithinInactivityWindow(incident, evidenceTime);
    }

    public static String identityOf(String correlationKey) {
        if (correlationKey == null || correlationKey.isBlank()) {
            return correlationKey;
        }
        int index = correlationKey.lastIndexOf(":g");
        if (index <= 0) {
            return correlationKey;
        }
        String suffix = correlationKey.substring(index + 2);
        if (suffix.isEmpty()) {
            return correlationKey;
        }
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return correlationKey;
            }
        }
        return correlationKey.substring(0, index);
    }

    public List<Incident> family(String identity) {
        List<Incident> family = new ArrayList<>();
        if (identity == null || identity.isBlank()) {
            return family;
        }
        incidentRepository.findFirstByCorrelationKeyOrderByLastSeenAtDesc(identity).ifPresent(family::add);
        for (Incident generation : incidentRepository.findByCorrelationKeyStartsWithOrderByLastSeenAtDesc(identity + ":g")) {
            if (family.stream().noneMatch(existing -> existing.getId().equals(generation.getId()))) {
                family.add(generation);
            }
        }
        return family;
    }

    public Optional<Incident> findReusableEpisode(String identity, Instant evidenceTime) {
        return family(identity).stream()
                .filter(candidate -> canReuse(candidate, evidenceTime))
                .max(Comparator.comparing(this::lastActivity, Comparator.nullsLast(Instant::compareTo)));
    }

    public Optional<Incident> findLatestInFamily(String identity) {
        return findPreviousSimilar(identity, null);
    }

    public Optional<Incident> findPreviousSimilar(String identity, Long excludeId) {
        return family(identity).stream()
                .filter(candidate -> excludeId == null || !excludeId.equals(candidate.getId()))
                .max(Comparator.comparing(Incident::getLastSeenAt, Comparator.nullsLast(Instant::compareTo)));
    }

    public Optional<Incident> findLatestOpen(String identity) {
        return family(identity).stream()
                .filter(this::isOpen)
                .max(Comparator.comparing(this::lastActivity, Comparator.nullsLast(Instant::compareTo)));
    }

    public String nextFreeCorrelationKey(String identity) {
        Incident existing = incidentRepository.findFirstByCorrelationKeyOrderByLastSeenAtDesc(identity).orElse(null);
        if (existing == null) {
            return identity;
        }
        Incident current = existing;
        for (int hop = 0; hop < 32; hop++) {
            String candidate = identity + ":g" + current.getId();
            Incident occupant = incidentRepository.findFirstByCorrelationKeyOrderByLastSeenAtDesc(candidate).orElse(null);
            if (occupant == null) {
                return candidate;
            }
            current = occupant;
        }
        return identity + ":g" + Long.toHexString(System.nanoTime());
    }
}
