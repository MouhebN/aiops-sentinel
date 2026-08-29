package com.aiops.backend.incident;

import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventResponse;
import com.aiops.backend.event.Severity;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record IncidentResponse(
        Long id,
        String title,
        String category,
        String deviceId,
        String deviceName,
        DeviceType deviceType,
        String location,
        Severity severity,
        IncidentStatus status,
        DeviceStatus resultingStatus,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant createdAt,
        Instant lastActivityAt,
        Instant recoveredAt,
        Instant resolvedAt,
        long durationMinutes,
        int eventCount,
        boolean acknowledged,
        Instant acknowledgedAt,
        Long previousSimilarIncidentId,
        boolean recurring,
        EventResponse latestEvent,
        List<EventResponse> relatedEvents,
        List<IncidentComponentMatchResponse> relatedComponents
) {

    public static IncidentResponse from(Incident incident) {
        return from(incident, List.of());
    }

    public static IncidentResponse from(Incident incident, List<IncidentComponentMatchResponse> relatedComponents) {
        List<Event> events = incident.getEvents()
                .stream()
                .sorted(Comparator.comparing(Event::getOccurredAt).reversed())
                .toList();
        Event latestEvent = events.isEmpty() ? null : events.getFirst();

        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getCategory(),
                incident.getDeviceId(),
                incident.getDeviceName(),
                incident.getDeviceType(),
                incident.getLocation(),
                incident.getSeverity(),
                incident.getStatus() == null ? null : incident.getStatus().forApi(),
                incident.getResultingStatus(),
                incident.getFirstSeenAt(),
                incident.getLastSeenAt(),
                incident.getCreatedAt(),
                incident.getLastActivityAt(),
                incident.getRecoveredAt(),
                incident.getResolvedAt(),
                incident.durationMinutes(),
                incident.getEventCount(),
                incident.isAcknowledged() || incident.getStatus() == IncidentStatus.ACKNOWLEDGED,
                incident.getAcknowledgedAt(),
                incident.getPreviousSimilarIncidentId(),
                incident.isRecurring(),
                latestEvent == null ? null : EventResponse.from(latestEvent),
                events.stream().map(EventResponse::from).toList(),
                relatedComponents == null ? List.of() : relatedComponents
        );
    }
}
