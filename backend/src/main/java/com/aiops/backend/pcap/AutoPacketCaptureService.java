package com.aiops.backend.pcap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AutoPacketCaptureService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoPacketCaptureService.class);

    private final AutoCapturePolicy policy;
    private final PacketCaptureJobService jobService;

    public AutoPacketCaptureService(
            AutoCapturePolicy policy,
            PacketCaptureJobService jobService
    ) {
        this.policy = policy;
        this.jobService = jobService;
    }

    /**
     * Best-effort automatic capture. Never throws to the incident-creation path.
     */
    public void captureIfEligible(Long incidentId) {
        if (incidentId == null) {
            return;
        }
        try {
            if (jobService.hasAutomaticJob(incidentId)) {
                LOGGER.info("Skipping auto-capture for incident {}: AUTO job already exists", incidentId);
                return;
            }
            PacketCaptureJobResponse job = jobService.startAutomatic(incidentId, policy);
            if (job != null) {
                LOGGER.info(
                        "Auto-capture {} for incident {} trigger={} status={}",
                        job.id(),
                        incidentId,
                        job.trigger(),
                        job.status()
                );
            }
        } catch (ResponseStatusException exception) {
            LOGGER.warn("Auto-capture skipped for incident {}: {}", incidentId, exception.getReason());
        } catch (RuntimeException exception) {
            LOGGER.warn("Auto-capture failed for incident {}: {}", incidentId, exception.getMessage());
        }
    }
}
