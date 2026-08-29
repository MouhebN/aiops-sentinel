package com.aiops.backend.incident;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.incident")
public class IncidentProperties {

    private long correlationInactivityMinutes = 30L;

    public long getCorrelationInactivityMinutes() {
        return correlationInactivityMinutes;
    }

    public void setCorrelationInactivityMinutes(long correlationInactivityMinutes) {
        this.correlationInactivityMinutes = correlationInactivityMinutes;
    }
}
