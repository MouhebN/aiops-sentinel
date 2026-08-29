package com.aiops.backend.incident;

import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.Severity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String correlationKey;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private DeviceType deviceType;

    @Column(nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceStatus resultingStatus;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    private Instant createdAt;

    private Instant lastActivityAt;

    private Instant recoveredAt;

    private Long previousSimilarIncidentId;

    @Column(nullable = false)
    private int eventCount;

    @Column(nullable = false)
    private boolean acknowledged;

    private Instant acknowledgedAt;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE})
    @JoinTable(
            name = "incident_events",
            joinColumns = @JoinColumn(name = "incident_id"),
            inverseJoinColumns = @JoinColumn(name = "event_id")
    )
    private Set<Event> events = new LinkedHashSet<>();

    protected Incident() {
    }

    public Incident(String correlationKey, String category, Event event, DeviceStatus resultingStatus, String title) {
        this.correlationKey = correlationKey;
        this.category = category;
        this.title = title;
        this.deviceId = event.getDeviceId();
        this.deviceName = event.getDeviceName();
        this.deviceType = event.getDeviceType();
        this.location = event.getLocation();
        this.severity = event.getSeverity();
        this.status = IncidentStatus.ACTIVE;
        this.resultingStatus = resultingStatus;
        Instant occurredAt = event.getOccurredAt() == null ? Instant.now() : event.getOccurredAt();
        this.firstSeenAt = occurredAt;
        this.lastSeenAt = occurredAt;
        this.createdAt = occurredAt;
        this.lastActivityAt = occurredAt;
        this.eventCount = 0;
        addEvent(event);
    }

    public void addEvent(Event event) {
        events.add(event);
        eventCount = events.size();
        Instant occurredAt = event.getOccurredAt();
        if (occurredAt != null) {
            if (firstSeenAt == null || occurredAt.isBefore(firstSeenAt)) {
                firstSeenAt = occurredAt;
            }
            recordActivity(occurredAt);
            if (createdAt == null) {
                createdAt = occurredAt;
            }
        }
        deviceName = event.getDeviceName();
        deviceType = event.getDeviceType();
        location = event.getLocation();
        if (rank(event.getSeverity()) > rank(severity)) {
            severity = event.getSeverity();
        }
    }

    /**
     * Advances activity timestamps when new evidence is attached.
     * {@code lastActivityAt} is authoritative for episode correlation.
     * {@code lastSeenAt} is kept in sync for list sort and legacy readers.
     * Never moves either timestamp backwards. Does not change status or acknowledgedAt.
     */
    public void recordActivity(Instant evidenceTime) {
        if (evidenceTime == null) {
            return;
        }
        if (lastSeenAt == null || evidenceTime.isAfter(lastSeenAt)) {
            lastSeenAt = evidenceTime;
        }
        if (lastActivityAt == null || evidenceTime.isAfter(lastActivityAt)) {
            lastActivityAt = evidenceTime;
        }
    }

    public void markActive(DeviceStatus status, String nextTitle) {
        if (this.status != null && this.status.isClosed()) {
            return;
        }
        if (this.status != IncidentStatus.ACKNOWLEDGED) {
            this.status = IncidentStatus.ACTIVE;
        }
        this.resultingStatus = status;
        this.title = nextTitle;
    }

    public void markRecovered(Event event, DeviceStatus status) {
        addEvent(event);
        this.status = IncidentStatus.RESOLVED;
        this.resultingStatus = status;
        this.recoveredAt = event.getOccurredAt() == null ? Instant.now() : event.getOccurredAt();
    }

    public void acknowledge() {
        if (this.status != null && this.status.isClosed()) {
            return;
        }
        this.status = IncidentStatus.ACKNOWLEDGED;
        this.acknowledged = true;
        this.acknowledgedAt = Instant.now();
    }

    public void unacknowledge() {
        if (this.status != null && this.status.isClosed()) {
            return;
        }
        this.status = IncidentStatus.ACTIVE;
        this.acknowledged = false;
        this.acknowledgedAt = null;
    }

    public void resolve() {
        this.status = IncidentStatus.RESOLVED;
        this.recoveredAt = Instant.now();
        this.resultingStatus = DeviceStatus.UP;
        if (!this.acknowledged) {
            this.acknowledged = true;
            this.acknowledgedAt = Instant.now();
        }
    }

    public void removeEvent(Event event) {
        if (!events.remove(event)) {
            return;
        }
        eventCount = events.size();
        if (events.isEmpty()) {
            return;
        }
        Event latest = events.stream()
                .max(Comparator.comparing(Event::getOccurredAt))
                .orElseThrow();
        firstSeenAt = events.stream()
                .map(Event::getOccurredAt)
                .min(Instant::compareTo)
                .orElse(firstSeenAt);
        lastSeenAt = latest.getOccurredAt();
        lastActivityAt = lastSeenAt;
        deviceName = latest.getDeviceName();
        deviceType = latest.getDeviceType();
        location = latest.getLocation();
        severity = events.stream()
                .map(Event::getSeverity)
                .max(Comparator.comparingInt(this::rank))
                .orElse(severity);
    }

    public long durationMinutes() {
        Instant start = createdAt != null ? createdAt : firstSeenAt;
        Instant end = recoveredAt == null ? Instant.now() : recoveredAt;
        return Math.max(0, Duration.between(start, end).toMinutes());
    }

    private int rank(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 3;
            case WARNING -> 2;
            case INFO -> 1;
        };
    }

    public Long getId() {
        return id;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public String getLocation() {
        return location;
    }

    public Severity getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public DeviceStatus getResultingStatus() {
        return resultingStatus;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getCreatedAt() {
        return createdAt != null ? createdAt : firstSeenAt;
    }

    /** Authoritative episode timestamp. Falls back to lastSeenAt for pre-lifecycle rows. */
    public Instant getLastActivityAt() {
        return lastActivityAt != null ? lastActivityAt : lastSeenAt;
    }

    public Instant getRecoveredAt() {
        return recoveredAt;
    }

    public Instant getResolvedAt() {
        return recoveredAt;
    }

    public Long getPreviousSimilarIncidentId() {
        return previousSimilarIncidentId;
    }

    public void setPreviousSimilarIncidentId(Long previousSimilarIncidentId) {
        this.previousSimilarIncidentId = previousSimilarIncidentId;
    }

    public boolean isRecurring() {
        return previousSimilarIncidentId != null;
    }

    public int getEventCount() {
        return eventCount;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Set<Event> getEvents() {
        return events;
    }
}
