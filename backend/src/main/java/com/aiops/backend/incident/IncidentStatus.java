package com.aiops.backend.incident;

public enum IncidentStatus {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED,
    /** @deprecated replaced by {@link #RESOLVED}; kept so existing rows still load. */
    RECOVERED;

    public boolean isOpen() {
        return this == ACTIVE || this == ACKNOWLEDGED;
    }

    public boolean isClosed() {
        return this == RESOLVED || this == RECOVERED;
    }

    public IncidentStatus forApi() {
        return this == RECOVERED ? RESOLVED : this;
    }
}
