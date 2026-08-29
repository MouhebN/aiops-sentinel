package com.aiops.backend.component;

import java.time.Instant;
import java.util.List;

public record ComponentConnectionCheckResponse(
        Long componentId,
        String componentName,
        boolean successful,
        ComponentStatus resultStatus,
        String message,
        List<String> details,
        Instant checkedAt
) {
}
