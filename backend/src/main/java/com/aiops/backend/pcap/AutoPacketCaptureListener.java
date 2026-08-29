package com.aiops.backend.pcap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;

@Component
public class AutoPacketCaptureListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoPacketCaptureListener.class);

    private final AutoPacketCaptureService autoPacketCaptureService;
    private final PcapCaptureProperties properties;
    private final Executor packetCaptureExecutor;
    private final TransactionTemplate requiresNew;

    public AutoPacketCaptureListener(
            AutoPacketCaptureService autoPacketCaptureService,
            PcapCaptureProperties properties,
            @Qualifier("packetCaptureExecutor") Executor packetCaptureExecutor,
            PlatformTransactionManager transactionManager
    ) {
        this.autoPacketCaptureService = autoPacketCaptureService;
        this.properties = properties;
        this.packetCaptureExecutor = packetCaptureExecutor;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIncidentEligible(IncidentEligibleForAutoCaptureEvent event) {
        if (event == null || event.incidentId() == null) {
            return;
        }
        LOGGER.info("IncidentEligibleForAutoCaptureEvent after commit incidentId={}", event.incidentId());
        Runnable task = () -> {
            try {
                // AFTER_COMMIT still has leftover transaction synchronization on this thread.
                // REQUIRES_NEW starts a real persistence transaction for the capture job.
                requiresNew.executeWithoutResult(status ->
                        autoPacketCaptureService.captureIfEligible(event.incidentId()));
            } catch (RuntimeException exception) {
                LOGGER.warn("Auto-capture listener failed for incident {}: {}", event.incidentId(), exception.getMessage());
            }
        };
        if (properties.getCapture().isAsync()) {
            packetCaptureExecutor.execute(task);
        } else {
            task.run();
        }
    }
}
