package com.aiops.backend;

import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.netflow.NetFlowAnomalyDetectionService;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class NetFlowIncidentLinkingTests {

    @Autowired
    private NetFlowAnomalyDetectionService anomalyDetectionService;

    @Autowired
    private NetworkFlowRepository networkFlowRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void netFlowEvidenceIsLinkedOnlyToMatchingIncidents() {
        Instant now = Instant.parse("2026-08-17T12:00:00Z");
        List<NetworkFlow> scanA = savePortScan("10.0.0.10", "10.0.0.20", now, 21, 22, 23, 80, 443);
        List<NetworkFlow> scanB = savePortScan("10.0.0.10", "10.0.0.30", now.plusSeconds(5), 21, 22, 23, 80, 443);

        List<NetworkFlow> imported = new ArrayList<>();
        imported.addAll(scanA);
        imported.addAll(scanB);
        anomalyDetectionService.analyze(null, imported);

        Set<Long> incidentIdsA = incidentIds(scanA);
        Set<Long> incidentIdsB = incidentIds(scanB);
        assertEquals(1, incidentIdsA.size());
        assertEquals(1, incidentIdsB.size());
        Long incidentA = incidentIdsA.iterator().next();
        Long incidentB = incidentIdsB.iterator().next();
        assertTrue(incidentA != null && incidentB != null && !incidentA.equals(incidentB));

        assertEquals(
                scanA.stream().map(NetworkFlow::getId).collect(Collectors.toSet()),
                networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(incidentA).stream()
                        .map(NetworkFlow::getId)
                        .collect(Collectors.toSet())
        );
        assertEquals(
                scanB.stream().map(NetworkFlow::getId).collect(Collectors.toSet()),
                networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(incidentB).stream()
                        .map(NetworkFlow::getId)
                        .collect(Collectors.toSet())
        );
    }

    @Test
    void icmpTypeCodeValuesDoNotBecomePortScanEvidence() {
        Instant now = Instant.parse("2026-08-24T15:18:00Z");
        List<NetworkFlow> icmpFlows = new ArrayList<>();
        int[] typeLikePorts = {8, 0, 3, 11, 13, 5, 4};
        for (int index = 0; index < typeLikePorts.length; index++) {
            Instant flowStart = now.plusSeconds(index);
            icmpFlows.add(networkFlowRepository.save(new NetworkFlow(
                    null,
                    flowStart,
                    flowStart.plusSeconds(1),
                    1000,
                    "10.0.0.10",
                    "10.0.0.1",
                    0,
                    typeLikePorts[index],
                    "ICMP",
                    6,
                    504,
                    "test-exporter",
                    0,
                    0,
                    UUID.randomUUID().toString().replace("-", ""),
                    "icmp type.code " + typeLikePorts[index] + ".0"
            )));
        }

        anomalyDetectionService.analyze(null, icmpFlows);

        for (NetworkFlow flow : icmpFlows) {
            NetworkFlow saved = networkFlowRepository.findById(flow.getId()).orElseThrow();
            assertTrue(!saved.isSuspicious());
            assertNull(saved.getAnomalyType());
            assertNull(saved.getIncidentId());
        }
        assertTrue(netFlowIncidentsFor("10.0.0.10", "10.0.0.1").isEmpty());
    }

    @Test
    void firewallDenySrcDstLabelsReceiveNetFlowEvidence() {
        Instant now = Instant.parse("2026-08-18T16:10:00Z");
        Event event = eventRepository.save(new Event(
                "syslog-bank-fw-01",
                "BANK-FW-01",
                DeviceType.FIREWALL,
                "lab",
                "FIREWALL_DENY",
                Severity.WARNING,
                "DENY SRC=192.168.0.15 DST=192.168.0.1 SPT=49152 DPT=22 PROTO=TCP",
                "firewall: DENY SRC=192.168.0.15 DST=192.168.0.1 SPT=49152 DPT=22 PROTO=TCP",
                "<134>Aug 18 16:10:00 BANK-FW-01 firewall: DENY SRC=192.168.0.15 DST=192.168.0.1 SPT=49152 DPT=22 PROTO=TCP",
                "192.168.0.15",
                "BANK-FW-01",
                "FIREWALL",
                "SYSLOG",
                now
        ));
        Incident syslogIncident = incidentRepository.save(new Incident(
                "syslog-bank-fw-01:SECURITY",
                "SECURITY",
                event,
                DeviceStatus.WARNING,
                "BANK-FW-01 security alert"
        ));

        List<NetworkFlow> scan = savePortScan("192.168.0.15", "192.168.0.1", now.plusSeconds(30), 21, 22, 23, 80, 443, 3306, 5432);
        anomalyDetectionService.analyze(null, scan);

        assertEquals(Set.of(syslogIncident.getId()), incidentIds(scan));
    }

    @Test
    void matchingSyslogIncidentReceivesNetFlowEvidenceAndUnrelatedIncidentsDoNot() {
        Instant now = Instant.parse("2026-08-17T13:00:00Z");
        Incident syslogIncident = saveSecuritySyslogIncident(
                "fw-syslog-1",
                "10.1.1.10",
                "10.1.1.20",
                now.minusSeconds(30)
        );
        Incident unrelated = saveAvailabilityIncident("server-1", now);

        List<NetworkFlow> matchingScan = savePortScan("10.1.1.10", "10.1.1.20", now, 21, 22, 23, 80, 443);
        List<NetworkFlow> otherScan = savePortScan("10.9.9.9", "10.8.8.8", now, 21, 22, 23, 80, 443);
        List<NetworkFlow> imported = new ArrayList<>();
        imported.addAll(matchingScan);
        imported.addAll(otherScan);

        anomalyDetectionService.analyze(null, imported);

        Set<Long> matchingIncidentIds = incidentIds(matchingScan);
        assertEquals(Set.of(syslogIncident.getId()), matchingIncidentIds);
        assertTrue(networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(unrelated.getId()).isEmpty());
        assertTrue(incidentIds(otherScan).stream().noneMatch(id -> id.equals(syslogIncident.getId())));
        assertTrue(incidentIds(otherScan).stream().noneMatch(id -> id.equals(unrelated.getId())));
    }

    @Test
    void staleRecoveredIncidentDoesNotReceiveNewNetFlowEvidence() {
        Instant staleTime = Instant.parse("2026-08-17T08:00:00Z");
        Instant now = Instant.parse("2026-08-17T14:00:00Z");
        Incident stale = saveSecuritySyslogIncident("fw-old", "10.2.2.2", "10.3.3.3", staleTime);
        stale.resolve();
        incidentRepository.save(stale);

        List<NetworkFlow> scan = savePortScan("10.2.2.2", "10.3.3.3", now, 21, 22, 23, 80, 443);
        anomalyDetectionService.analyze(null, scan);

        assertTrue(networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(stale.getId()).isEmpty());
        Set<Long> linkedIds = incidentIds(scan);
        assertEquals(1, linkedIds.size());
        assertTrue(!linkedIds.contains(stale.getId()));
    }

    @Test
    void portScanCreatesOneIncidentAndRepeatedAnalyzeDoesNotDuplicateIt() {
        Instant now = Instant.parse("2026-08-22T15:00:00Z");
        List<NetworkFlow> scan = savePortScan("10.20.30.40", "10.20.30.50", now, 21, 22, 23, 80, 443, 3306, 5432);

        anomalyDetectionService.analyze(null, scan);

        Set<Long> firstIds = incidentIds(scan);
        assertEquals(1, firstIds.size());
        Long incidentId = firstIds.iterator().next();
        Incident incident = incidentRepository.findWithEventsById(incidentId).orElseThrow();
        int eventCount = incident.getEventCount();
        assertEquals(7, eventCount);

        assertDoesNotThrow(() -> anomalyDetectionService.analyze(null, scan));

        assertEquals(Set.of(incidentId), incidentIds(scan));
        Incident afterRepeat = incidentRepository.findWithEventsById(incidentId).orElseThrow();
        assertEquals(eventCount, afterRepeat.getEventCount());
        assertEquals(1, netFlowIncidentsFor("10.20.30.40", "10.20.30.50").size());
    }

    @Test
    void existingGenerationCorrelationKeyDoesNotThrowOnNewPortScan() {
        Instant staleTime = Instant.parse("2026-01-01T00:00:00Z");
        Incident base = saveNetFlowKeyedIncident(
                "NETFLOW:PORT_SCAN:192.168.0.15:192.168.0.1",
                "192.168.0.15",
                "192.168.0.1",
                staleTime
        );
        base.resolve();
        incidentRepository.save(base);
        Incident generation = saveNetFlowKeyedIncident(
                "NETFLOW:PORT_SCAN:192.168.0.15:192.168.0.1:g" + base.getId(),
                "192.168.0.15",
                "192.168.0.1",
                staleTime.plusSeconds(60)
        );
        generation.resolve();
        incidentRepository.save(generation);

        Instant now = Instant.parse("2026-08-22T16:00:00Z");
        List<NetworkFlow> scan = savePortScan("192.168.0.15", "192.168.0.1", now, 21, 22, 23, 80, 443, 3306, 5432);

        assertDoesNotThrow(() -> anomalyDetectionService.analyze(null, scan));
        Set<Long> linked = incidentIds(scan);
        assertEquals(1, linked.size());
        assertTrue(!linked.contains(base.getId()));
        assertTrue(!linked.contains(generation.getId()));
    }

    private Incident saveNetFlowKeyedIncident(String correlationKey, String sourceIp, String destinationIp, Instant occurredAt) {
        Event event = eventRepository.save(new Event(
                "netflow-source-demo",
                "Demo NetFlow Collector",
                DeviceType.FIREWALL,
                "NetFlow demo",
                "NETFLOW_PORT_SCAN",
                Severity.CRITICAL,
                "NetFlow detected repeated TCP connections across multiple destination ports.",
                "sourceAddress=" + sourceIp + "; destinationAddress=" + destinationIp + "; destinationPort=22; anomalyType=PORT_SCAN",
                sourceIp + " -> " + destinationIp + ":22",
                sourceIp,
                null,
                null,
                "NETFLOW",
                occurredAt
        ));
        return incidentRepository.save(new Incident(
                correlationKey,
                "SECURITY",
                event,
                DeviceStatus.WARNING,
                "Possible port scan from " + sourceIp + " to " + destinationIp
        ));
    }

    private List<Incident> netFlowIncidentsFor(String sourceIp, String destinationIp) {
        String prefix = "NETFLOW:PORT_SCAN:" + sourceIp.toLowerCase() + ":" + destinationIp.toLowerCase();
        return incidentRepository.findAll().stream()
                .filter(incident -> incident.getCorrelationKey() != null && incident.getCorrelationKey().startsWith(prefix))
                .toList();
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

    private Incident saveSecuritySyslogIncident(String deviceId, String sourceIp, String destinationIp, Instant occurredAt) {
        Event event = eventRepository.save(new Event(
                deviceId,
                deviceId,
                DeviceType.FIREWALL,
                "lab",
                "POSSIBLE_PORT_SCAN",
                Severity.CRITICAL,
                "Possible port scan",
                "sourceAddress=" + sourceIp + "; destinationAddress=" + destinationIp + "; destinationPort=22",
                null,
                sourceIp,
                "firewall",
                "FIREWALL",
                "SYSLOG",
                occurredAt
        ));
        return incidentRepository.save(new Incident(
                deviceId + ":SECURITY",
                "SECURITY",
                event,
                DeviceStatus.WARNING,
                deviceId + " possible port scan detected"
        ));
    }

    private Incident saveAvailabilityIncident(String deviceId, Instant occurredAt) {
        Event event = eventRepository.save(new Event(
                deviceId,
                deviceId,
                DeviceType.SERVER,
                "lab",
                "PING_UNREACHABLE",
                Severity.CRITICAL,
                "Host unreachable",
                "ip=10.0.0.99",
                null,
                "10.0.0.99",
                null,
                null,
                "MONITORING",
                occurredAt
        ));
        return incidentRepository.save(new Incident(
                deviceId + ":AVAILABILITY",
                "AVAILABILITY",
                event,
                DeviceStatus.DOWN,
                deviceId + " availability problem"
        ));
    }

    private Set<Long> incidentIds(List<NetworkFlow> flows) {
        return flows.stream()
                .map(flow -> networkFlowRepository.findById(flow.getId()).orElseThrow().getIncidentId())
                .collect(Collectors.toSet());
    }
}
