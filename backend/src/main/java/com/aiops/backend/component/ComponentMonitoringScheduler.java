package com.aiops.backend.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class ComponentMonitoringScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentMonitoringScheduler.class);

    private final MonitoredComponentRepository componentRepository;
    private final ComponentCheckService checkService;

    public ComponentMonitoringScheduler(
            MonitoredComponentRepository componentRepository,
            ComponentCheckService checkService
    ) {
        this.componentRepository = componentRepository;
        this.checkService = checkService;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void checkEnabledComponents() {
        Instant now = Instant.now();
        componentRepository.findAllByEnabledTrueOrderByCreatedAtDesc().forEach(component -> {
            if (!ComponentMonitoringPolicy.shouldRunScheduledCheck(component, now)) {
                return;
            }
            MonitoredComponent current = componentRepository.findById(component.getId()).orElse(null);
            if (!ComponentMonitoringPolicy.shouldRunScheduledCheck(current, now)) {
                return;
            }
            try {
                checkService.checkComponent(current, now);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Scheduled check skipped after error: id={} name=\"{}\" reason={}",
                        current.getId(),
                        current.getName(),
                        exception.getMessage()
                );
            }
        });
    }
}
