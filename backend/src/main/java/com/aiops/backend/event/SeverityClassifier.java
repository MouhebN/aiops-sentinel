package com.aiops.backend.event;

import com.aiops.backend.device.DeviceStatus;

import java.util.Locale;
import java.util.Set;

public final class SeverityClassifier {

    private static final Set<String> CRITICAL_KEYWORDS = Set.of(
            "down",
            "offline",
            "unreachable",
            "disk full",
            "battery low",
            "blocked",
            "critical"
    );

    private static final Set<String> WARNING_KEYWORDS = Set.of(
            "high",
            "packet loss",
            "failed login",
            "port scan",
            "suspicious",
            "warning"
    );

    private SeverityClassifier() {
    }

    public static Severity classify(String eventType, String message) {
        String text = ((eventType == null ? "" : eventType) + " " + (message == null ? "" : message))
                .replace('_', ' ')
                .replace('-', ' ')
                .toLowerCase(Locale.ROOT);

        if (CRITICAL_KEYWORDS.stream().anyMatch(text::contains)) {
            return Severity.CRITICAL;
        }
        if (WARNING_KEYWORDS.stream().anyMatch(text::contains)) {
            return Severity.WARNING;
        }
        return Severity.INFO;
    }

    public static DeviceStatus toDeviceStatus(Severity severity) {
        return switch (severity) {
            case INFO -> DeviceStatus.UP;
            case WARNING -> DeviceStatus.WARNING;
            case CRITICAL -> DeviceStatus.DOWN;
        };
    }
}
