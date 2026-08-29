package com.aiops.backend.component;

import java.util.Collection;

/**
 * Rolls per-method check statuses into one operational status.
 * Individual check outcomes are not rewritten here.
 */
public final class ComponentStatusAggregator {

    private ComponentStatusAggregator() {
    }

    public static ComponentStatus aggregate(Collection<ComponentStatus> checkStatuses) {
        if (checkStatuses == null || checkStatuses.isEmpty()) {
            return ComponentStatus.UNKNOWN;
        }

        boolean anyUp = false;
        boolean anyDown = false;
        boolean anyDegraded = false;
        boolean anyWarning = false;

        for (ComponentStatus status : checkStatuses) {
            if (status == null || status == ComponentStatus.UNKNOWN) {
                continue;
            }
            switch (status) {
                case UP -> anyUp = true;
                case DOWN -> anyDown = true;
                case DEGRADED -> anyDegraded = true;
                case WARNING -> anyWarning = true;
                default -> {
                }
            }
        }

        if (!anyUp && !anyDown && !anyDegraded && !anyWarning) {
            return ComponentStatus.UNKNOWN;
        }
        if (anyDown && !anyUp && !anyDegraded && !anyWarning) {
            return ComponentStatus.DOWN;
        }
        if (anyDown || anyDegraded) {
            return ComponentStatus.DEGRADED;
        }
        if (anyWarning) {
            return ComponentStatus.WARNING;
        }
        return ComponentStatus.UP;
    }
}
