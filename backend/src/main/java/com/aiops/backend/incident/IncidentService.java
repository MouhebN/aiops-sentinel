package com.aiops.backend.incident;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.Severity;
import com.aiops.backend.event.SeverityClassifier;
import com.aiops.backend.pcap.IncidentAutoCaptureCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class IncidentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository repository;
    private final EventRepository eventRepository;
    private final AuditLogService auditLogService;
    private final IncidentCorrelationPolicy correlationPolicy;
    private final IncidentComponentBinder componentBinder;
    private final ApplicationEventPublisher eventPublisher;
    private final IncidentAutoCaptureCoordinator autoCaptureCoordinator;

    public IncidentService(
            IncidentRepository repository,
            EventRepository eventRepository,
            AuditLogService auditLogService,
            IncidentCorrelationPolicy correlationPolicy,
            IncidentComponentBinder componentBinder,
            ApplicationEventPublisher eventPublisher,
            IncidentAutoCaptureCoordinator autoCaptureCoordinator
    ) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.auditLogService = auditLogService;
        this.correlationPolicy = correlationPolicy;
        this.componentBinder = componentBinder;
        this.eventPublisher = eventPublisher;
        this.autoCaptureCoordinator = autoCaptureCoordinator;
    }

    @Transactional
    public void correlate(Event event) {
        correlate(event, true);
    }

    @Transactional
    public void correlate(Event event, boolean publishCreated) {
        if (!isIncidentSignal(event)) {
            return;
        }

        String category = categoryFor(event.getEventType());
        String identity = event.getDeviceId() + ":" + category;
        DeviceStatus resultingStatus = SeverityClassifier.toDeviceStatus(event.getSeverity());
        Instant evidenceTime = event.getOccurredAt() == null ? Instant.now() : event.getOccurredAt();

        if (isRecoverySignal(event, resultingStatus)) {
            Incident open = correlationPolicy.findReusableEpisode(identity, evidenceTime)
                    .or(() -> correlationPolicy.findLatestOpen(identity))
                    .orElse(null);
            if (open != null) {
                open.markRecovered(event, DeviceStatus.UP);
                LOGGER.info(
                        "Correlated recovery event to incident: incidentId={}, eventId={}, correlationKey={}, status={}",
                        open.getId(),
                        event.getId(),
                        open.getCorrelationKey(),
                        open.getStatus()
                );
                auditLogService.log(
                        AuditAction.INCIDENT_RESOLVED,
                        "INCIDENT",
                        open.getId() == null ? null : open.getId().toString(),
                        "Incident recovered for " + open.getDeviceName()
                );
            }
            return;
        }

        Incident reusable = correlationPolicy.findReusableEpisode(identity, evidenceTime).orElse(null);
        boolean created = reusable == null;
        Incident incident;
        if (reusable != null) {
            reusable.markActive(resultingStatus, titleFor(category, event));
            reusable.addEvent(event);
            incident = reusable;
        } else {
            String key = correlationPolicy.nextFreeCorrelationKey(identity);
            incident = new Incident(
                    key,
                    category,
                    event,
                    resultingStatus,
                    titleFor(category, event)
            );
            correlationPolicy.findPreviousSimilar(identity, null)
                    .ifPresent(previous -> incident.setPreviousSimilarIncidentId(previous.getId()));
        }
        Incident savedIncident = repository.save(incident);
        if (created && publishCreated && savedIncident.getId() != null) {
            eventPublisher.publishEvent(new IncidentCreatedEvent(savedIncident.getId()));
        }
        autoCaptureCoordinator.evaluateAndTrigger(
                savedIncident.getId(),
                created ? "incident-created" : "syslog-evidence-attached"
        );
        LOGGER.info(
                "Correlated event to incident: incidentId={}, eventId={}, correlationKey={}, status={}, eventCount={}",
                savedIncident.getId(),
                event.getId(),
                savedIncident.getCorrelationKey(),
                savedIncident.getStatus(),
                savedIncident.getEventCount()
        );
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> list() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "lastSeenAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(Long id) {
        Incident incident = repository.findWithEventsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
        return toResponse(incident);
    }

    @Transactional
    public List<IncidentResponse> rebuild() {
        repository.deleteAll();
        eventRepository.findAll(Sort.by(Sort.Direction.ASC, "occurredAt"))
                .forEach(event -> correlate(event, false));
        auditLogService.log(AuditAction.INCIDENT_REBUILT, "INCIDENT", null, "Rebuilt incidents from stored events");
        return list();
    }

    @Transactional
    public IncidentResponse acknowledge(Long id) {
        Incident incident = find(id);
        incident.acknowledge();
        auditLogService.log(
                AuditAction.INCIDENT_ACKNOWLEDGED,
                "INCIDENT",
                incident.getId().toString(),
                "Acknowledged incident " + incident.getTitle()
        );
        return toResponse(incident);
    }

    @Transactional
    public IncidentResponse unacknowledge(Long id) {
        Incident incident = find(id);
        incident.unacknowledge();
        auditLogService.log(
                AuditAction.INCIDENT_UNACKNOWLEDGED,
                "INCIDENT",
                incident.getId().toString(),
                "Removed acknowledgement for " + incident.getTitle()
        );
        return toResponse(incident);
    }

    @Transactional
    public IncidentResponse resolve(Long id) {
        Incident incident = find(id);
        if (!incident.getStatus().isClosed()) {
            incident.resolve();
            auditLogService.log(
                    AuditAction.INCIDENT_RESOLVED,
                    "INCIDENT",
                    incident.getId().toString(),
                    "Resolved incident " + incident.getTitle()
            );
        }
        return toResponse(incident);
    }

    @Transactional
    public void delete(Long id) {
        Incident incident = find(id);
        repository.delete(incident);
        auditLogService.log(
                AuditAction.INCIDENT_DELETED,
                "INCIDENT",
                id.toString(),
                "Deleted incident " + incident.getTitle()
        );
    }

    @Transactional
    public void rebuildFromEventsWithoutAudit() {
        repository.deleteAll();
        eventRepository.findAll(Sort.by(Sort.Direction.ASC, "occurredAt"))
                .forEach(event -> correlate(event, false));
    }

    private Incident find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }

    private IncidentResponse toResponse(Incident incident) {
        return IncidentResponse.from(incident, componentBinder.relatedComponents(incident));
    }

    private boolean isIncidentSignal(Event event) {
        DeviceStatus status = SeverityClassifier.toDeviceStatus(event.getSeverity());
        return event.getSeverity() != Severity.INFO
                || event.getEventType().equals("METRIC_THRESHOLD_RECOVERED")
                || status == DeviceStatus.WARNING
                || status == DeviceStatus.DEGRADED
                || status == DeviceStatus.DOWN;
    }

    private boolean isRecoverySignal(Event event, DeviceStatus status) {
        return event.getEventType().equals("METRIC_THRESHOLD_RECOVERED") || status == DeviceStatus.UP;
    }

    private String categoryFor(String eventType) {
        if (eventType.contains("RTSP") || eventType.contains("CAMERA") || eventType.contains("STREAM")) {
            return "VIDEO_STREAM";
        }
        if (eventType.contains("UPS") || eventType.contains("BATTERY") || eventType.contains("POWER")) {
            return "POWER";
        }
        if (eventType.contains("PORT_SCAN")
                || eventType.contains("SUSPICIOUS")
                || eventType.contains("LOGIN")
                || eventType.contains("AUTH")
                || eventType.contains("FIREWALL")) {
            return "SECURITY";
        }
        if (eventType.contains("METRIC_THRESHOLD")) {
            return "METRIC_THRESHOLD";
        }
        if (eventType.contains("UNREACHABLE")
                || eventType.contains("DOWN")
                || eventType.contains("PING")
                || eventType.contains("TCP")
                || eventType.contains("HTTP")
                || eventType.contains("STATUS")
                || eventType.contains("INTERFACE")
                || eventType.contains("SERVICE")
                || eventType.contains("APPLICATION")) {
            return "AVAILABILITY";
        }
        return eventType;
    }

    private String titleFor(String category, Event event) {
        if (category.equals("SECURITY") && event.getEventType().contains("PORT_SCAN")) {
            return event.getDeviceName() + " possible port scan detected";
        }
        return switch (category) {
            case "VIDEO_STREAM" -> event.getDeviceName() + " video stream problem";
            case "POWER" -> event.getDeviceName() + " power or UPS risk";
            case "SECURITY" -> event.getDeviceName() + " security alert";
            case "METRIC_THRESHOLD" -> event.getDeviceName() + " metric threshold breach";
            case "AVAILABILITY" -> event.getDeviceName() + " availability problem";
            default -> event.getDeviceName() + " " + event.getEventType().toLowerCase().replace('_', ' ');
        };
    }
}
