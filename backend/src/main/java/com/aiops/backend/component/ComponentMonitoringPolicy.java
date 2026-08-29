package com.aiops.backend.component;

import java.time.Instant;

final class ComponentMonitoringPolicy {

    private ComponentMonitoringPolicy() {
    }

    static boolean shouldRunScheduledCheck(MonitoredComponent component, Instant now) {
        if (component == null || !component.isEnabled()) {
            return false;
        }
        Instant lastCheckedAt = component.getLastCheckedAt();
        if (lastCheckedAt == null) {
            return true;
        }
        Instant nextCheckAt = lastCheckedAt.plusSeconds(Math.max(component.getCheckIntervalSeconds(), 5));
        return !nextCheckAt.isAfter(now);
    }

    static boolean shouldAcceptExternalStatusUpdate(MonitoredComponent component) {
        return component != null && component.isEnabled();
    }
}
