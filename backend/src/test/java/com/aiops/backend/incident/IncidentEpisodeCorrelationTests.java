package com.aiops.backend.incident;

import com.aiops.backend.ai.AiContextBuilderService;
import com.aiops.backend.ai.AiIncidentContextResponse;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.EventIngestionRequest;
import com.aiops.backend.event.EventService;
import com.aiops.backend.event.Severity;
import com.aiops.backend.netflow.NetFlowAnomalyDetectionService;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class IncidentEpisodeCorrelationTests {

    private static final Instant T0 = Instant.parse("2026-08-24T16:00:00Z");

    @Autowired
    private EventService eventService;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private NetFlowAnomalyDetectionService anomalyDetectionService;

    @Autowired
    private NetworkFlowRepository networkFlowRepository;

    @Autowired
    private AiContextBuilderService aiContextBuilderService;

    @Test
    void recentMatchingEventReusesActiveIncident() {
        String deviceId = "episode-active";
        ingestPortScan(deviceId, "10.0.0.10", "10.10.10.20", T0);
        ingestPortScan(deviceId, "10.0.0.10", "10.10.10.20", T0.plus(Duration.ofMinutes(5)));

        List<Incident> incidents = securityIncidents(deviceId);
        assertThat(incidents).hasSize(1);
        Incident incident = incidents.getFirst();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ACTIVE);
        assertThat(incident.getEventCount()).isEqualTo(2);
        assertThat(incident.getCreatedAt()).isEqualTo(T0);
        assertThat(incident.getLastActivityAt()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
    }

    @Test
    void recentMatchingEventReusesAcknowledgedIncident() {
        String deviceId = "episode-acked";
        ingestPortScan(deviceId, "10.0.0.11", "10.10.10.21", T0);
        Incident created = latestSecurity(deviceId);
        incidentService.acknowledge(created.getId());
        Instant acknowledgedAt = incidentRepository.findById(created.getId()).orElseThrow().getAcknowledgedAt();

        ingestPortScan(deviceId, "10.0.0.11", "10.10.10.21", T0.plus(Duration.ofMinutes(10)));

        List<Incident> incidents = securityIncidents(deviceId);
        assertThat(incidents).hasSize(1);
        Incident incident = incidents.getFirst();
        assertThat(incident.getId()).isEqualTo(created.getId());
        assertThat(incident.getStatus().forApi()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
        assertThat(incident.getEventCount()).isEqualTo(2);
        assertThat(incident.getLastActivityAt()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
        assertThat(incident.getLastSeenAt()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
        assertThat(incident.getAcknowledgedAt()).isEqualTo(acknowledgedAt);
    }

    @Test
    void matchingEventAfterInactivityWindowCreatesNewIncident() {
        String deviceId = "episode-gap";
        ingestPortScan(deviceId, "10.0.0.12", "10.10.10.22", T0);
        Incident first = latestSecurity(deviceId);
        ingestPortScan(deviceId, "10.0.0.12", "10.10.10.22", T0.plus(Duration.ofMinutes(31)));

        List<Incident> incidents = securityIncidents(deviceId);
        assertThat(incidents).hasSize(2);
        Incident newest = latestSecurity(deviceId);
        assertThat(newest.getId()).isNotEqualTo(first.getId());
        assertThat(newest.getPreviousSimilarIncidentId()).isEqualTo(first.getId());
        assertThat(newest.isRecurring()).isTrue();
        assertThat(newest.getStatus()).isEqualTo(IncidentStatus.ACTIVE);
        assertThat(first.getStatus()).isEqualTo(IncidentStatus.ACTIVE);
    }

    @Test
    void resolvedIncidentIsNeverReused() {
        String deviceId = "episode-resolved";
        ingestPortScan(deviceId, "10.0.0.13", "10.10.10.23", T0);
        Incident first = latestSecurity(deviceId);
        IncidentResponse resolved = incidentService.resolve(first.getId());
        assertThat(resolved.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.resolvedAt()).isNotNull();

        ingestPortScan(deviceId, "10.0.0.13", "10.10.10.23", T0.plus(Duration.ofMinutes(1)));

        List<Incident> incidents = securityIncidents(deviceId);
        assertThat(incidents).hasSize(2);
        Incident newest = latestSecurity(deviceId);
        assertThat(newest.getId()).isNotEqualTo(first.getId());
        assertThat(incidentRepository.findById(first.getId()).orElseThrow().getStatus().forApi())
                .isEqualTo(IncidentStatus.RESOLVED);
        assertThat(newest.getPreviousSimilarIncidentId()).isEqualTo(first.getId());
    }

    @Test
    void resolvingSetsResolvedAt() {
        String deviceId = "episode-resolved-at";
        ingestPortScan(deviceId, "10.0.0.14", "10.10.10.24", T0);
        Incident created = latestSecurity(deviceId);
        assertThat(created.getResolvedAt()).isNull();

        IncidentResponse resolved = incidentService.resolve(created.getId());
        assertThat(resolved.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.resolvedAt()).isNotNull();
        assertThat(resolved.resolvedAt()).isEqualTo(resolved.recoveredAt());
        assertThat(incidentRepository.findById(created.getId()).orElseThrow().getLastActivityAt()).isEqualTo(T0);
    }

    @Test
    void newEvidenceUpdatesLastActivityAt() {
        String deviceId = "episode-activity";
        ingestPortScan(deviceId, "10.0.0.15", "10.10.10.25", T0);
        Instant later = T0.plus(Duration.ofMinutes(20));
        ingestPortScan(deviceId, "10.0.0.15", "10.10.10.25", later);

        Incident incident = latestSecurity(deviceId);
        assertThat(incident.getCreatedAt()).isEqualTo(T0);
        assertThat(incident.getLastActivityAt()).isEqualTo(later);
        assertThat(incident.getLastSeenAt()).isEqualTo(later);
    }

    @Test
    void syslogAndNetFlowUseTheSameEpisodeLogic() {
        String deviceId = "episode-shared";
        Instant stale = T0.minus(Duration.ofDays(3));
        ingestPortScan(deviceId, "10.0.0.10", "10.10.10.20", stale);
        Incident old = latestSecurity(deviceId);

        ingestPortScan(deviceId, "10.0.0.10", "10.10.10.20", T0);
        Incident freshSyslog = latestSecurity(deviceId);
        assertThat(freshSyslog.getId()).isNotEqualTo(old.getId());

        List<NetworkFlow> scan = savePortScan("10.0.0.10", "10.10.10.20", T0.plusSeconds(30), 21, 22, 23, 80, 443);
        anomalyDetectionService.analyze(null, scan);

        assertThat(incidentIds(scan)).containsExactly(freshSyslog.getId());
        assertThat(networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(old.getId())).isEmpty();
        Incident updated = incidentRepository.findWithEventsById(freshSyslog.getId()).orElseThrow();
        assertThat(updated.getLastActivityAt()).isAfterOrEqualTo(T0.plusSeconds(30));
        assertThat(updated.getLastSeenAt()).isEqualTo(updated.getLastActivityAt());
        assertThat(updated.getStatus()).isEqualTo(IncidentStatus.ACTIVE);
    }

    @Test
    void acknowledgedIncidentAdvancesLastActivityWhenFreshNetFlowReusesSamePorts() {
        String deviceId = "episode-acked-netflow";
        ingestPortScan(deviceId, "10.0.0.17", "10.10.10.27", T0);
        List<NetworkFlow> firstScan = savePortScan(
                "10.0.0.17",
                "10.10.10.27",
                T0.plusSeconds(20),
                21, 22, 23, 80, 443
        );
        anomalyDetectionService.analyze(null, firstScan);

        Incident incident = latestSecurity(deviceId);
        assertThat(incidentIds(firstScan)).containsExactly(incident.getId());
        Instant firstActivity = incidentRepository.findWithEventsById(incident.getId()).orElseThrow().getLastActivityAt();
        incidentService.acknowledge(incident.getId());
        Instant acknowledgedAt = incidentRepository.findById(incident.getId()).orElseThrow().getAcknowledgedAt();
        int eventCountAfterAck = incidentRepository.findWithEventsById(incident.getId()).orElseThrow().getEventCount();

        Instant secondScanStart = T0.plus(Duration.ofMinutes(5));
        List<NetworkFlow> secondScan = savePortScan(
                "10.0.0.17",
                "10.10.10.27",
                secondScanStart,
                21, 22, 23, 80, 443
        );
        anomalyDetectionService.analyze(null, secondScan);

        assertThat(incidentIds(secondScan)).containsExactly(incident.getId());
        Incident updated = incidentRepository.findWithEventsById(incident.getId()).orElseThrow();
        assertThat(updated.getStatus().forApi()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
        assertThat(updated.getAcknowledgedAt()).isEqualTo(acknowledgedAt);
        assertThat(updated.getEventCount()).isEqualTo(eventCountAfterAck);
        assertThat(updated.getLastActivityAt()).isAfter(firstActivity);
        assertThat(updated.getLastActivityAt()).isAfterOrEqualTo(secondScanStart);
        assertThat(updated.getLastSeenAt()).isEqualTo(updated.getLastActivityAt());
    }

    @Test
    void reanalyzingAlreadyLinkedNetFlowDoesNotAdvanceLastActivityAt() {
        String deviceId = "episode-netflow-reimport";
        ingestPortScan(deviceId, "10.0.0.18", "10.10.10.28", T0);
        List<NetworkFlow> scan = savePortScan("10.0.0.18", "10.10.10.28", T0.plusSeconds(10), 21, 22, 23, 80, 443);
        anomalyDetectionService.analyze(null, scan);

        Incident afterFirst = incidentRepository.findWithEventsById(latestSecurity(deviceId).getId()).orElseThrow();
        Instant activity = afterFirst.getLastActivityAt();
        int eventCount = afterFirst.getEventCount();

        anomalyDetectionService.analyze(null, scan);

        Incident afterRepeat = incidentRepository.findWithEventsById(afterFirst.getId()).orElseThrow();
        assertThat(afterRepeat.getLastActivityAt()).isEqualTo(activity);
        assertThat(afterRepeat.getEventCount()).isEqualTo(eventCount);
        assertThat(incidentIds(scan)).containsExactly(afterFirst.getId());
    }

    @Test
    void freshNetFlowAfterThreeDayIncidentCreatesNewIncident() {
        String deviceId = "episode-old-netflow";
        Instant stale = Instant.parse("2026-08-21T16:00:00Z");
        ingestPortScan(deviceId, "10.0.0.10", "10.10.10.20", stale);
        Incident old = latestSecurity(deviceId);

        List<NetworkFlow> scan = savePortScan("10.0.0.10", "10.10.10.20", T0, 21, 22, 23, 80, 443);
        anomalyDetectionService.analyze(null, scan);

        Set<Long> linked = incidentIds(scan);
        assertThat(linked).hasSize(1);
        assertThat(linked).doesNotContain(old.getId());
        assertThat(networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(old.getId())).isEmpty();
    }

    @Test
    void freshSyslogAndNetFlowCorrelateIntoTheSameNewIncident() {
        String deviceId = "episode-fresh-both";
        Instant stale = Instant.parse("2026-08-21T10:00:00Z");
        ingestPortScan(deviceId, "10.0.0.10", "10.10.10.20", stale);
        Incident old = latestSecurity(deviceId);

        ingestPortScan(deviceId, "10.0.0.10", "10.10.10.20", T0);
        Incident fresh = latestSecurity(deviceId);
        assertThat(fresh.getId()).isNotEqualTo(old.getId());
        assertThat(fresh.getPreviousSimilarIncidentId()).isEqualTo(old.getId());

        List<NetworkFlow> scan = savePortScan("10.0.0.10", "10.10.10.20", T0.plusSeconds(20), 21, 22, 23, 80, 443, 3306);
        anomalyDetectionService.analyze(null, scan);

        assertThat(incidentIds(scan)).containsExactly(fresh.getId());
        assertThat(securityIncidents(deviceId)).hasSize(2);
        assertThat(networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(fresh.getId())).hasSize(scan.size());
    }

    @Test
    void previousSimilarIncidentRemainsAvailableForHistoryAndAi() {
        String deviceId = "episode-similar-ai";
        ingestPortScan(deviceId, "10.0.0.16", "10.10.10.26", T0);
        Incident first = latestSecurity(deviceId);
        incidentService.resolve(first.getId());
        ingestPortScan(deviceId, "10.0.0.16", "10.10.10.26", T0.plus(Duration.ofMinutes(2)));
        Incident second = latestSecurity(deviceId);

        assertThat(second.getPreviousSimilarIncidentId()).isEqualTo(first.getId());
        AiIncidentContextResponse context = aiContextBuilderService.build(second.getId());
        assertThat(context.previousSimilarIncidentId()).isEqualTo(first.getId());
        assertThat(context.createdAt()).isEqualTo(T0.plus(Duration.ofMinutes(2)));
        assertThat(context.lastActivityAt()).isEqualTo(T0.plus(Duration.ofMinutes(2)));
        assertThat(context.resolvedAt()).isNull();
        assertThat(context.previousSimilarIncidents())
                .extracting(AiIncidentContextResponse.SimilarIncidentSnapshot::id)
                .contains(first.getId());
        assertThat(context.previousSimilarIncidents())
                .filteredOn(snapshot -> snapshot.id().equals(first.getId()))
                .first()
                .extracting(AiIncidentContextResponse.SimilarIncidentSnapshot::status)
                .isEqualTo(IncidentStatus.RESOLVED);
        assertThat(context.previousSimilarIncidents())
                .filteredOn(snapshot -> snapshot.id().equals(first.getId()))
                .first()
                .extracting(AiIncidentContextResponse.SimilarIncidentSnapshot::resolvedAt)
                .isNotNull();
    }

    private void ingestPortScan(String deviceId, String sourceIp, String destinationIp, Instant occurredAt) {
        eventService.ingest(new EventIngestionRequest(
                deviceId,
                deviceId,
                DeviceType.FIREWALL,
                "lab",
                "POSSIBLE_PORT_SCAN",
                Severity.CRITICAL,
                "Possible port scan",
                "sourceAddress=" + sourceIp + "; destinationAddress=" + destinationIp
                        + "; destinationPort=22; anomalyType=PORT_SCAN",
                "DENY TCP " + sourceIp + " -> " + destinationIp + ":22",
                sourceIp,
                deviceId,
                "FIREWALL",
                "SYSLOG",
                occurredAt
        ));
    }

    private List<Incident> securityIncidents(String deviceId) {
        return incidentRepository.findAll().stream()
                .filter(incident -> deviceId.equals(incident.getDeviceId()))
                .filter(incident -> "SECURITY".equalsIgnoreCase(incident.getCategory()))
                .sorted(Comparator.comparing(Incident::getLastSeenAt))
                .toList();
    }

    private Incident latestSecurity(String deviceId) {
        return securityIncidents(deviceId).stream()
                .max(Comparator.comparing(Incident::getLastSeenAt))
                .orElseThrow();
    }

    private List<NetworkFlow> savePortScan(String sourceIp, String destinationIp, Instant start, int... ports) {
        List<NetworkFlow> flows = new ArrayList<>();
        for (int index = 0; index < ports.length; index++) {
            Instant flowStart = start.plusSeconds(index);
            NetworkFlow flow = new NetworkFlow(
                    null,
                    flowStart,
                    flowStart.plusSeconds(1),
                    1000,
                    sourceIp,
                    destinationIp,
                    40000 + index,
                    ports[index],
                    "TCP",
                    4,
                    256,
                    "test-exporter",
                    1,
                    2,
                    UUID.randomUUID().toString().replace("-", ""),
                    sourceIp + " -> " + destinationIp + ":" + ports[index]
            );
            flows.add(networkFlowRepository.save(flow));
        }
        return flows;
    }

    private Set<Long> incidentIds(List<NetworkFlow> flows) {
        return flows.stream()
                .map(flow -> networkFlowRepository.findById(flow.getId()).orElseThrow().getIncidentId())
                .collect(Collectors.toSet());
    }
}
