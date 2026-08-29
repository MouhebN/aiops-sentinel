package com.aiops.backend.netflow;

import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits NetFlow SECURITY incidents in a nested transaction so a unique
 * {@code correlation_key} race can roll back without failing Import Latest.
 */
@Component
public class NetFlowIncidentPersistence {

    private final IncidentRepository incidentRepository;
    private final EventRepository eventRepository;

    public NetFlowIncidentPersistence(
            IncidentRepository incidentRepository,
            EventRepository eventRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Incident persistNewIncident(
            String correlationKey,
            Event event,
            DeviceStatus resultingStatus,
            String title
    ) {
        Event savedEvent = eventRepository.saveAndFlush(event);
        Incident incident = new Incident(correlationKey, "SECURITY", savedEvent, resultingStatus, title);
        return incidentRepository.saveAndFlush(incident);
    }

    public boolean isCorrelationKeyConflict(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("correlation_key")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return exception instanceof DataIntegrityViolationException;
    }
}
