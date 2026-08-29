package com.aiops.backend.component;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentMonitoringPolicyTests {

    @Test
    void stoppedComponentIsNeverDue() {
        MonitoredComponent component = component(false, Instant.now().minusSeconds(120));
        assertThat(ComponentMonitoringPolicy.shouldRunScheduledCheck(component, Instant.now())).isFalse();
        assertThat(ComponentMonitoringPolicy.shouldAcceptExternalStatusUpdate(component)).isFalse();
    }

    @Test
    void enabledComponentWithoutLastCheckIsDue() {
        MonitoredComponent component = component(true, null);
        assertThat(ComponentMonitoringPolicy.shouldRunScheduledCheck(component, Instant.now())).isTrue();
    }

    @Test
    void enabledComponentInsideIntervalIsNotDue() {
        Instant now = Instant.parse("2026-08-23T12:00:00Z");
        MonitoredComponent component = component(true, now.minusSeconds(10));
        assertThat(ComponentMonitoringPolicy.shouldRunScheduledCheck(component, now)).isFalse();
    }

    @Test
    void enabledComponentAfterIntervalIsDue() {
        Instant now = Instant.parse("2026-08-23T12:00:00Z");
        MonitoredComponent component = component(true, now.minusSeconds(30));
        assertThat(ComponentMonitoringPolicy.shouldRunScheduledCheck(component, now)).isTrue();
    }

    private MonitoredComponent component(boolean enabled, Instant lastCheckedAt) {
        MonitoredComponent component = new MonitoredComponent(
                "CAM-POLICY",
                com.aiops.backend.device.DeviceType.IP_CAMERA,
                "10.10.10.40",
                null,
                554,
                161,
                "public",
                "1.3.6.1.2.1.1.1.0",
                "lab",
                Criticality.MEDIUM,
                Set.of(MonitoringMethod.PING),
                30,
                enabled
        );
        if (lastCheckedAt != null) {
            component.updateStatus(ComponentStatus.UP, lastCheckedAt, lastCheckedAt, null, "ok");
        }
        return component;
    }
}
