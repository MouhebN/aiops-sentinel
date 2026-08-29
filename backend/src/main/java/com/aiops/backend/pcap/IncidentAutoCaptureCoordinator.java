package com.aiops.backend.pcap;

import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * First-eligible automatic capture. Evaluates current enriched evidence and publishes
 * {@link IncidentEligibleForAutoCaptureEvent} at most once per incident (job uniqueness).
 * Does not mutate incident timestamps or status.
 */
@Service
public class IncidentAutoCaptureCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentAutoCaptureCoordinator.class);

    private final AutoCapturePolicy policy;
    private final PacketCaptureJobService jobService;
    private final IncidentRepository incidentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public IncidentAutoCaptureCoordinator(
            AutoCapturePolicy policy,
            PacketCaptureJobService jobService,
            IncidentRepository incidentRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.policy = policy;
        this.jobService = jobService;
        this.incidentRepository = incidentRepository;
        this.eventPublisher = eventPublisher;
    }

    public void evaluateAndTrigger(Long incidentId, String reason) {
        if (incidentId == null) {
            return;
        }
        try {
            incidentRepository.findWithEventsById(incidentId)
                    .ifPresent(incident -> evaluateAndTrigger(incident, reason));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Auto-capture eligibility evaluation failed for incident {}: {}",
                    incidentId,
                    exception.getMessage()
            );
        }
    }

    public void evaluateAndTrigger(Incident incident, String reason) {
        if (incident == null || incident.getId() == null) {
            return;
        }
        try {
            boolean eligible = policy.shouldCapture(incident);
            if (!eligible) {
                LOGGER.debug(
                        "Auto-capture not eligible incidentId={} reason={}",
                        incident.getId(),
                        reason
                );
                return;
            }
            if (jobService.hasAutomaticJob(incident.getId())) {
                LOGGER.info(
                        "Skipping auto-capture for incident {}: AUTO job already exists reason={}",
                        incident.getId(),
                        reason
                );
                return;
            }
            LOGGER.info(
                    "Incident {} first became eligible for auto-capture reason={}",
                    incident.getId(),
                    reason
            );
            eventPublisher.publishEvent(new IncidentEligibleForAutoCaptureEvent(incident.getId()));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Auto-capture eligibility evaluation failed for incident {}: {}",
                    incident.getId(),
                    exception.getMessage()
            );
        }
    }
}
