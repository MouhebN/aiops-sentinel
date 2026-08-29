package com.aiops.backend;

import com.aiops.backend.component.ComponentStatus;
import com.aiops.backend.component.Criticality;
import com.aiops.backend.component.MonitoredComponent;
import com.aiops.backend.component.MonitoredComponentRepository;
import com.aiops.backend.component.MonitoredComponentService;
import com.aiops.backend.component.MonitoringMethod;
import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import com.aiops.backend.pcap.PacketCaptureAnalysis;
import com.aiops.backend.pcap.PacketCaptureAnalysisRepository;
import com.aiops.backend.topology.ComponentRelationRepository;
import com.aiops.backend.topology.ComponentRelationResponse;
import com.aiops.backend.topology.ComponentRelationService;
import com.aiops.backend.topology.ComponentRelationType;
import com.aiops.backend.topology.SaveComponentRelationRequest;
import com.aiops.backend.topology.TopologyNodeResponse;
import com.aiops.backend.topology.TopologyResponse;
import com.aiops.backend.topology.TopologySecurityState;
import com.aiops.backend.topology.TopologyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TopologyServiceTests {

    @Autowired
    private TopologyService topologyService;

    @Autowired
    private ComponentRelationService relationService;

    @Autowired
    private ComponentRelationRepository relationRepository;

    @Autowired
    private MonitoredComponentRepository componentRepository;

    @Autowired
    private MonitoredComponentService componentService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private NetworkFlowRepository networkFlowRepository;

    @Autowired
    private PacketCaptureAnalysisRepository packetCaptureAnalysisRepository;

    @Test
    void topologyReturnsMonitoredComponentsAndRelationLinks() {
        MonitoredComponent firewall = saveComponent("FW-TOPO-1", DeviceType.FIREWALL, "10.0.0.1");
        MonitoredComponent router = saveComponent("RTR-TOPO-1", DeviceType.ROUTER, "10.0.1.1");
        relationService.create(new SaveComponentRelationRequest(
                firewall.getId(),
                router.getId(),
                ComponentRelationType.CONNECTED_TO,
                "wan"
        ));

        TopologyResponse topology = topologyService.getTopology();

        assertThat(topology.nodes().stream().map(TopologyNodeResponse::componentId))
                .contains(firewall.getId(), router.getId());
        assertThat(topology.links()).anyMatch(link ->
                link.source().equals("component:" + firewall.getId())
                        && link.target().equals("component:" + router.getId())
                        && link.relationType() == ComponentRelationType.CONNECTED_TO
        );
        assertThat(topology.activeAttacks()).isEmpty();
    }

    @Test
    void activeSecurityIncidentMapsDestinationIpAndKeepsOperationalStatus() {
        MonitoredComponent server = saveComponent("SRV-TOPO-1", DeviceType.SERVER, "10.10.10.20");
        server.updateStatus(ComponentStatus.UP, Instant.now(), Instant.now(), null, "up");
        componentRepository.save(server);
        MonitoredComponent firewall = saveComponent("FW-TOPO-2", DeviceType.FIREWALL, "10.0.0.1");

        Incident incident = savePortScanIncident(
                "fw-syslog",
                firewall.getName(),
                "10.0.0.10",
                "10.10.10.20",
                Instant.now()
        );
        networkFlowRepository.save(flow(incident.getId(), "10.0.0.10", "10.10.10.20", 22));
        packetCaptureAnalysisRepository.save(new PacketCaptureAnalysis(
                incident.getId(),
                "scan.pcap",
                "application/vnd.tcpdump.pcap",
                128,
                20,
                1024,
                "10.0.0.10",
                "10.10.10.20",
                "TCP",
                "22",
                "SYN scan",
                "Port scan evidence"
        ));

        TopologyResponse topology = topologyService.getTopology();
        TopologyNodeResponse serverNode = node(topology, server.getId());
        TopologyNodeResponse firewallNode = node(topology, firewall.getId());

        assertThat(serverNode.operationalStatus()).isEqualTo(ComponentStatus.UP);
        assertThat(serverNode.monitoringEnabled()).isTrue();
        assertThat(serverNode.securityState()).isEqualTo(TopologySecurityState.UNDER_ATTACK);
        assertThat(serverNode.activeIncidentIds()).contains(incident.getId());
        assertThat(serverNode.highestIncidentSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(serverNode.evidenceTypes()).contains("SYSLOG", "NETFLOW", "PCAP");
        assertThat(firewallNode.securityState()).isEqualTo(TopologySecurityState.SUSPICIOUS_ACTIVITY);
        assertThat(topology.externalEntities()).anyMatch(entity ->
                "external:10.0.0.10".equals(entity.id()) && "EXTERNAL".equals(entity.kind())
        );
        assertThat(topology.activeAttacks()).anySatisfy(attack -> {
            assertThat(attack.source()).isEqualTo("external:10.0.0.10");
            assertThat(attack.target()).isEqualTo("component:" + server.getId());
            assertThat(attack.incidentId()).isEqualTo(incident.getId());
            assertThat(attack.type()).isEqualTo("PORT_SCAN");
            assertThat(attack.sourceIp()).isEqualTo("10.0.0.10");
            assertThat(attack.blocked()).isTrue();
            assertThat(attack.evidence()).contains("SYSLOG", "NETFLOW", "PCAP");
        });
        assertThat(componentRepository.findAll()).noneMatch(component ->
                "10.0.0.10".equals(component.getIpAddress()) && component.getName().toLowerCase().contains("attacker")
        );
        long before = componentRepository.count();
        topologyService.getTopology();
        assertThat(componentRepository.count()).isEqualTo(before);
    }

    @Test
    void recoveredIncidentDoesNotProduceAttackEdge() {
        saveComponent("SRV-TOPO-2", DeviceType.SERVER, "10.10.10.30");
        Incident incident = savePortScanIncident("fw-old", "FW-OLD", "10.9.9.9", "10.10.10.30", Instant.now());
        incident.resolve();
        incidentRepository.save(incident);

        TopologyResponse topology = topologyService.getTopology();
        assertThat(topology.activeAttacks()).noneMatch(attack -> attack.incidentId().equals(incident.getId()));
        assertThat(topology.nodes()).filteredOn(node -> "10.10.10.30".equals(node.ipAddress()))
                .allMatch(node -> node.securityState() == TopologySecurityState.NORMAL);
    }

    @Test
    void acknowledgedIncidentStillProducesAttackEdge() {
        MonitoredComponent server = saveComponent("SRV-TOPO-ACK", DeviceType.SERVER, "10.10.10.21");
        Incident incident = savePortScanIncident(
                "fw-ack",
                "FW-ACK",
                "10.0.0.11",
                "10.10.10.21",
                Instant.now()
        );
        incident.acknowledge();
        incidentRepository.save(incident);

        TopologyResponse topology = topologyService.getTopology();
        assertThat(node(topology, server.getId()).securityState())
                .isEqualTo(TopologySecurityState.UNDER_ATTACK);
        assertThat(topology.activeAttacks()).anyMatch(attack -> attack.incidentId().equals(incident.getId()));
    }

    @Test
    void staleOpenIncidentDoesNotProduceAttackEdge() {
        saveComponent("SRV-TOPO-STALE", DeviceType.SERVER, "10.10.10.31");
        Incident incident = savePortScanIncident(
                "fw-stale",
                "FW-STALE",
                "10.9.9.8",
                "10.10.10.31",
                Instant.now().minus(java.time.Duration.ofDays(3))
        );

        TopologyResponse topology = topologyService.getTopology();
        assertThat(topology.activeAttacks()).noneMatch(attack -> attack.incidentId().equals(incident.getId()));
        assertThat(topology.nodes()).filteredOn(node -> "10.10.10.31".equals(node.ipAddress()))
                .allMatch(node -> node.securityState() == TopologySecurityState.NORMAL);
    }

    @Test
    void duplicateRelationIsRejectedAndDeleteRemovesLink() {
        MonitoredComponent left = saveComponent("SW-TOPO-1", DeviceType.SWITCH, "10.10.10.1");
        MonitoredComponent right = saveComponent("ATM-TOPO-1", DeviceType.ATM, "10.10.10.30");
        ComponentRelationResponse created = relationService.create(new SaveComponentRelationRequest(
                left.getId(),
                right.getId(),
                ComponentRelationType.CONNECTED_TO,
                null
        ));
        assertThatThrownBy(() -> relationService.create(new SaveComponentRelationRequest(
                left.getId(),
                right.getId(),
                ComponentRelationType.CONNECTED_TO,
                "again"
        )))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThatThrownBy(() -> relationService.create(new SaveComponentRelationRequest(
                left.getId(),
                left.getId(),
                ComponentRelationType.CONNECTED_TO,
                null
        )))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        relationService.delete(created.id());
        assertThat(relationRepository.findById(created.id())).isEmpty();
        assertThat(topologyService.getTopology().links()).noneMatch(link ->
                link.source().equals("component:" + left.getId())
                        && link.target().equals("component:" + right.getId())
        );
    }

    @Test
    void deletingComponentRemovesItsRelations() {
        MonitoredComponent source = saveComponent("CAM-TOPO-1", DeviceType.IP_CAMERA, "10.10.10.40");
        MonitoredComponent target = saveComponent("SW-TOPO-2", DeviceType.SWITCH, "10.10.10.2");
        relationService.create(new SaveComponentRelationRequest(
                target.getId(),
                source.getId(),
                ComponentRelationType.CONNECTED_TO,
                null
        ));
        componentService.delete(source.getId());

        assertThat(topologyService.getTopology().links()).noneMatch(link ->
                link.target().equals("component:" + source.getId()) || link.source().equals("component:" + source.getId())
        );
    }

    private TopologyNodeResponse node(TopologyResponse topology, Long componentId) {
        return topology.nodes().stream()
                .filter(item -> componentId.equals(item.componentId()))
                .findFirst()
                .orElseThrow();
    }

    private MonitoredComponent saveComponent(String name, DeviceType type, String ip) {
        return componentRepository.save(new MonitoredComponent(
                name,
                type,
                ip,
                null,
                null,
                161,
                "public",
                "1.3.6.1.2.1.1.1.0",
                "lab",
                Criticality.MEDIUM,
                Set.of(MonitoringMethod.PING),
                30,
                true
        ));
    }

    private Incident savePortScanIncident(
            String deviceId,
            String deviceName,
            String sourceIp,
            String destinationIp,
            Instant occurredAt
    ) {
        Event event = eventRepository.save(new Event(
                deviceId,
                deviceName,
                DeviceType.FIREWALL,
                "lab",
                "FIREWALL_DENY",
                Severity.CRITICAL,
                "DENY TCP " + sourceIp + ":1 -> " + destinationIp + ":22",
                "protocol=TCP; sourceAddress=" + sourceIp + "; destinationAddress=" + destinationIp + "; destinationPort=22",
                "<134>Aug 23 14:00:00 " + deviceName + " DENY TCP " + sourceIp + ":45122 -> " + destinationIp + ":22",
                sourceIp,
                deviceName,
                "FIREWALL",
                "SYSLOG",
                occurredAt
        ));
        Event scan = eventRepository.save(new Event(
                deviceId,
                deviceName,
                DeviceType.FIREWALL,
                "lab",
                "POSSIBLE_PORT_SCAN",
                Severity.CRITICAL,
                "Possible port scan",
                "sourceAddress=" + sourceIp + "; destinationAddress=" + destinationIp + "; anomalyType=PORT_SCAN",
                null,
                sourceIp,
                deviceName,
                "FIREWALL",
                "SYSLOG",
                occurredAt.plusSeconds(1)
        ));
        Incident incident = new Incident(
                deviceId + ":SECURITY:" + UUID.randomUUID(),
                "SECURITY",
                event,
                DeviceStatus.WARNING,
                deviceName + " security alert"
        );
        incident.addEvent(scan);
        return incidentRepository.save(incident);
    }

    private NetworkFlow flow(Long incidentId, String sourceIp, String destinationIp, int port) {
        NetworkFlow flow = new NetworkFlow(
                null,
                Instant.now(),
                Instant.now().plusSeconds(1),
                1000,
                sourceIp,
                destinationIp,
                40000,
                port,
                "TCP",
                1,
                64,
                "lab",
                1,
                2,
                UUID.randomUUID().toString().replace("-", ""),
                sourceIp + " -> " + destinationIp
        );
        flow.markSuspicious(com.aiops.backend.netflow.NetFlowAnomalyType.PORT_SCAN, "scan", incidentId);
        return flow;
    }
}
