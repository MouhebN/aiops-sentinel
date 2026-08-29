package com.aiops.backend.event;

import com.aiops.backend.device.Device;
import com.aiops.backend.device.DeviceRepository;
import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.incident.IncidentService;
import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final IncidentService incidentService;
    private final IncidentRepository incidentRepository;
    private final AuditLogService auditLogService;

    public EventService(
            EventRepository eventRepository,
            DeviceRepository deviceRepository,
            IncidentService incidentService,
            IncidentRepository incidentRepository,
            AuditLogService auditLogService
    ) {
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.incidentService = incidentService;
        this.incidentRepository = incidentRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Event ingest(EventIngestionRequest request) {
        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        Severity severity = request.severity() == null
                ? SeverityClassifier.classify(request.eventType(), request.message())
                : request.severity();
        DeviceStatus status = SeverityClassifier.toDeviceStatus(severity);

        Event event = new Event(
                request.deviceId(),
                request.deviceName(),
                request.deviceType(),
                request.location(),
                request.eventType(),
                severity,
                request.message(),
                request.details(),
                request.rawLog(),
                request.sourceIp(),
                request.syslogSourceName(),
                request.parsingProfile(),
                request.eventSource(),
                occurredAt
        );

        Device device = deviceRepository.findById(request.deviceId())
                .orElseGet(() -> new Device(
                        request.deviceId(),
                        request.deviceName(),
                        request.deviceType(),
                        request.location(),
                        status,
                        occurredAt
                ));
        device.updateFromEvent(request.deviceName(), request.deviceType(), request.location(), status, occurredAt);
        deviceRepository.save(device);

        LOGGER.info(
                "Persisting event: deviceId={}, deviceName={}, eventType={}, severity={}, eventSource={}, occurredAt={}",
                request.deviceId(),
                request.deviceName(),
                request.eventType(),
                severity,
                request.eventSource(),
                occurredAt
        );
        Event savedEvent = eventRepository.save(event);
        incidentService.correlate(savedEvent);
        LOGGER.info(
                "Event persisted: eventId={}, deviceId={}, eventType={}, severity={}",
                savedEvent.getId(),
                savedEvent.getDeviceId(),
                savedEvent.getEventType(),
                savedEvent.getSeverity()
        );
        return savedEvent;
    }

    @Transactional(readOnly = true)
    public List<Event> search(
            String deviceId,
            DeviceType deviceType,
            String eventType,
            Severity severity,
            Instant from,
            Instant to
    ) {
        Specification<Event> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (deviceId != null && !deviceId.isBlank()) {
                predicates.add(builder.equal(root.get("deviceId"), deviceId));
            }
            if (deviceType != null) {
                predicates.add(builder.equal(root.get("deviceType"), deviceType));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(builder.equal(root.get("eventType"), eventType));
            }
            if (severity != null) {
                predicates.add(builder.equal(root.get("severity"), severity));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), to));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };

        return eventRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "occurredAt"));
    }

    @Transactional
    public void delete(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        String deviceName = event.getDeviceName();
        incidentRepository.findAll()
                .forEach(incident -> {
                    incident.removeEvent(event);
                    if (incident.getEvents().isEmpty()) {
                        incidentRepository.delete(incident);
                    }
                });
        eventRepository.delete(event);
        incidentService.rebuildFromEventsWithoutAudit();
        auditLogService.log(
                AuditAction.EVENT_DELETED,
                "EVENT",
                id.toString(),
                "Deleted event for " + deviceName
        );
    }

    @Transactional
    public int deleteOlderThan(int days) {
        if (days < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Days must be at least 1");
        }
        Instant cutoff = Instant.now().minusSeconds(days * 24L * 60L * 60L);
        List<Event> events = eventRepository.findByOccurredAtBefore(cutoff);
        if (events.isEmpty()) {
            return 0;
        }
        incidentRepository.deleteAll();
        eventRepository.deleteAll(events);
        incidentService.rebuildFromEventsWithoutAudit();
        auditLogService.log(
                AuditAction.OLD_EVENTS_PURGED,
                "EVENT",
                null,
                "Purged " + events.size() + " events older than " + days + " days"
        );
        return events.size();
    }
}
