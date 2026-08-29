package com.aiops.backend.component;

import com.aiops.backend.ai.AiContextBuilderService;
import com.aiops.backend.ai.AiIncidentContextResponse;
import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentComponentMatchResponse;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.incident.IncidentResponse;
import com.aiops.backend.incident.IncidentService;
import com.aiops.backend.metric.MetricSample;
import com.aiops.backend.metric.MetricSampleRepository;
import com.aiops.backend.metric.MetricSampleResponse;
import com.aiops.backend.metric.MetricSampleService;
import com.aiops.backend.netflow.NetFlowAnomalyType;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import com.aiops.backend.topology.TopologyNodeResponse;
import com.aiops.backend.topology.TopologyResponse;
import com.aiops.backend.topology.TopologySecurityState;
import com.aiops.backend.topology.TopologyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ComponentIdentityResolutionTests {

    @Autowired
    private ComponentIdentityResolver identityResolver;

    @Autowired
    private MonitoredComponentService componentService;

    @Autowired
    private ComponentNetworkInterfaceService interfaceService;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private NetworkFlowRepository networkFlowRepository;

    @Autowired
    private MetricSampleRepository metricSampleRepository;

    @Autowired
    private MetricSampleService metricSampleService;

    @Autowired
    private TopologyService topologyService;

    @Autowired
    private AiContextBuilderService aiContextBuilderService;

    @Test
    void primaryManagementIpResolvesComponent() {
        MonitoredComponentResponse server = bankServer();

        assertThat(identityResolver.resolveComponent("172.30.30.20"))
                .isPresent()
                .get()
                .extracting(MonitoredComponent::getId, MonitoredComponent::getName)
                .containsExactly(server.id(), "BANK-SRV-01");
    }

    @Test
    void secondaryInterfaceIpResolvesSameComponent() {
        MonitoredComponentResponse server = bankServerWithServiceLan();

        var primary = identityResolver.resolveByIp("172.30.30.20").orElseThrow();
        var secondary = identityResolver.resolveByIp("10.10.10.20").orElseThrow();

        assertThat(primary.component().getId()).isEqualTo(server.id());
        assertThat(secondary.component().getId()).isEqualTo(server.id());
        assertThat(secondary.matchedIp()).isEqualTo("10.10.10.20");
        assertThat(secondary.matchedInterfaceName()).isEqualTo("Service LAN");
        assertThat(secondary.matchedPrimary()).isFalse();
    }

    @Test
    void unknownExternalIpResolvesNoComponent() {
        bankServerWithServiceLan();

        assertThat(identityResolver.resolveComponent("10.0.0.10")).isEmpty();
        assertThat(identityResolver.resolveByIp("10.0.0.10")).isEmpty();
    }

    @Test
    void labServiceLanIpResolvesBankServer() {
        bankServerWithServiceLan();

        assertThat(identityResolver.resolveComponent("10.10.10.20"))
                .get()
                .extracting(MonitoredComponent::getName)
                .isEqualTo("BANK-SRV-01");
    }

    @Test
    void existingIncidentWithDestinationAliasGetsComponentContextWithoutRecreate() {
        MonitoredComponentResponse server = bankServer();
        Incident incident = savePortScanIncident("fw-syslog", "BANK-FW-01", "10.0.0.10", "10.10.10.20");

        IncidentResponse before = incidentService.get(incident.getId());
        assertThat(before.relatedComponents()).noneMatch(match -> "BANK-SRV-01".equals(match.name()));

        addServiceLan(server.id());

        IncidentResponse after = incidentService.get(incident.getId());
        IncidentComponentMatchResponse target = targetOf(after);
        assertThat(target.name()).isEqualTo("BANK-SRV-01");
        assertThat(target.id()).isEqualTo(server.id());
        assertThat(target.primaryIp()).isEqualTo("172.30.30.20");
        assertThat(target.matchedIp()).isEqualTo("10.10.10.20");
        assertThat(target.relation()).isEqualTo("TARGET");
        assertThat(after.relatedComponents()).noneMatch(match -> "10.0.0.10".equals(match.matchedIp()));
    }

    @Test
    void latestMetricsLoadByComponentIdAfterSecondaryIpResolution() {
        MonitoredComponentResponse server = bankServerWithServiceLan();
        metricSampleRepository.save(new MetricSample(
                server.id(),
                server.name(),
                "icmpReachable",
                1.0,
                "bool",
                "PING",
                Instant.now()
        ));
        Incident incident = savePortScanIncident("fw-syslog", "BANK-FW-01", "10.0.0.10", "10.10.10.20");

        IncidentComponentMatchResponse target = targetOf(incidentService.get(incident.getId()));
        List<MetricSampleResponse> metrics = metricSampleService.listComponentSamples(target.id(), 24);

        assertThat(target.id()).isEqualTo(server.id());
        assertThat(metrics).anyMatch(sample ->
                "icmpReachable".equals(sample.metricName()) && sample.componentId().equals(server.id())
        );
        assertThat(metrics).noneMatch(sample -> "10.10.10.20".equals(sample.source()));
    }

    @Test
    void topologyMapsAliasDestinationToBankServerAndKeepsAttackerExternal() {
        MonitoredComponentResponse server = bankServerWithServiceLan();
        componentService.updateStatus(server.id(), new UpdateComponentStatusRequest(
                ComponentStatus.UP,
                Instant.now(),
                Instant.now(),
                null
        ));
        Incident incident = savePortScanIncident("fw-syslog", "BANK-FW-01", "10.0.0.10", "10.10.10.20");
        networkFlowRepository.save(flow(incident.getId(), "10.0.0.10", "10.10.10.20", 22));

        TopologyResponse topology = topologyService.getTopology();
        TopologyNodeResponse serverNode = topology.nodes().stream()
                .filter(node -> server.id().equals(node.componentId()))
                .findFirst()
                .orElseThrow();

        assertThat(serverNode.securityState()).isEqualTo(TopologySecurityState.UNDER_ATTACK);
        assertThat(serverNode.activeIncidentIds()).contains(incident.getId());
        assertThat(topology.externalEntities()).anyMatch(entity ->
                "external:10.0.0.10".equals(entity.id()) && "EXTERNAL".equals(entity.kind())
        );
        assertThat(topology.activeAttacks()).anySatisfy(attack -> {
            assertThat(attack.source()).isEqualTo("external:10.0.0.10");
            assertThat(attack.target()).isEqualTo("component:" + server.id());
            assertThat(attack.sourceIp()).isEqualTo("10.0.0.10");
        });
        assertThat(identityResolver.resolveComponent("10.0.0.10")).isEmpty();
    }

    @Test
    void syslogAndNetFlowUseTheSameResolver() {
        MonitoredComponentResponse server = bankServerWithServiceLan();
        Incident syslogIncident = savePortScanIncident("fw-syslog", "BANK-FW-01", "10.0.0.10", "10.10.10.20");

        Event netflowEvent = eventRepository.save(new Event(
                "nfcapd",
                "netflow-collector",
                DeviceType.OTHER,
                "lab",
                "POSSIBLE_PORT_SCAN",
                Severity.CRITICAL,
                "NetFlow port scan",
                "anomalyType=PORT_SCAN",
                null,
                "10.0.0.10",
                "netflow-collector",
                "GENERIC",
                "NETFLOW",
                Instant.now()
        ));
        Incident netflowIncident = incidentRepository.save(new Incident(
                "nfcapd:SECURITY:" + UUID.randomUUID(),
                "SECURITY",
                netflowEvent,
                DeviceStatus.WARNING,
                "NetFlow security alert"
        ));
        networkFlowRepository.save(flow(netflowIncident.getId(), "10.0.0.10", "10.10.10.20", 22));

        IncidentComponentMatchResponse syslogTarget = targetOf(incidentService.get(syslogIncident.getId()));
        IncidentComponentMatchResponse netflowTarget = targetOf(incidentService.get(netflowIncident.getId()));

        assertThat(syslogTarget.id()).isEqualTo(server.id());
        assertThat(netflowTarget.id()).isEqualTo(server.id());
        assertThat(syslogTarget.matchedIp()).isEqualTo("10.10.10.20");
        assertThat(netflowTarget.matchedIp()).isEqualTo("10.10.10.20");
    }

    @Test
    void aiContextReceivesResolvedTargetComponentAndMatchedNetworkIp() {
        bankServerWithServiceLan();
        Incident incident = savePortScanIncident("fw-syslog", "BANK-FW-01", "10.0.0.10", "10.10.10.20");

        AiIncidentContextResponse context = aiContextBuilderService.build(incident.getId());

        assertThat(context.component()).isNotNull();
        assertThat(context.component().name()).isEqualTo("BANK-SRV-01");
        assertThat(context.component().ipAddress()).isEqualTo("172.30.30.20");
        assertThat(context.component().matchedNetworkIp()).isEqualTo("10.10.10.20");
        assertThat(context.component().matchedInterfaceName()).isEqualTo("Service LAN");
    }

    @Test
    void deletingInterfaceRemovesAliasWithoutDeletingComponentOrMetrics() {
        MonitoredComponentResponse server = bankServerWithServiceLan();
        Long interfaceId = server.networkInterfaces().getFirst().id();
        metricSampleRepository.save(new MetricSample(
                server.id(),
                server.name(),
                "icmpReachable",
                1.0,
                "bool",
                "PING",
                Instant.now()
        ));

        interfaceService.delete(server.id(), interfaceId);

        assertThat(componentService.get(server.id()).name()).isEqualTo("BANK-SRV-01");
        assertThat(componentService.get(server.id()).networkInterfaces()).isEmpty();
        assertThat(identityResolver.resolveComponent("10.10.10.20")).isEmpty();
        assertThat(identityResolver.resolveComponent("172.30.30.20")).isPresent();
        assertThat(metricSampleService.listComponentSamples(server.id(), 24))
                .anyMatch(sample -> "icmpReachable".equals(sample.metricName()));
    }

    @Test
    void monitoringPrimaryIpIsUnchangedAfterAddingServiceLanAlias() {
        MonitoredComponentResponse server = bankServerWithServiceLan();

        assertThat(server.ipAddress()).isEqualTo("172.30.30.20");
        assertThat(componentService.get(server.id()).ipAddress()).isEqualTo("172.30.30.20");
        assertThat(componentService.get(server.id()).networkInterfaces())
                .anyMatch(item -> "10.10.10.20".equals(item.ipAddress()));
    }

    private MonitoredComponentResponse bankServer() {
        return componentService.create(new SaveMonitoredComponentRequest(
                "BANK-SRV-01",
                DeviceType.SERVER,
                "172.30.30.20",
                null,
                22,
                161,
                "public",
                "1.3.6.1.2.1.1.1.0",
                "lab",
                Criticality.HIGH,
                Set.of(MonitoringMethod.PING),
                30,
                true
        ));
    }

    private MonitoredComponentResponse bankServerWithServiceLan() {
        MonitoredComponentResponse server = bankServer();
        addServiceLan(server.id());
        return componentService.get(server.id());
    }

    private void addServiceLan(Long componentId) {
        interfaceService.create(componentId, new SaveComponentNetworkInterfaceRequest(
                "Service LAN",
                "10.10.10.20",
                NetworkInterfaceRole.SERVICE,
                false
        ));
    }

    private IncidentComponentMatchResponse targetOf(IncidentResponse incident) {
        return incident.relatedComponents().stream()
                .filter(match -> "TARGET".equals(match.relation()))
                .findFirst()
                .orElseThrow();
    }

    private Incident savePortScanIncident(
            String deviceId,
            String deviceName,
            String sourceIp,
            String destinationIp
    ) {
        Instant occurredAt = Instant.now();
        Event event = eventRepository.save(new Event(
                deviceId,
                deviceName,
                DeviceType.FIREWALL,
                "lab",
                "FIREWALL_DENY",
                Severity.CRITICAL,
                "DENY TCP " + sourceIp + ":1 -> " + destinationIp + ":22",
                "protocol=TCP; sourceAddress=" + sourceIp + "; destinationAddress=" + destinationIp + "; destinationPort=22",
                "<134>Aug 24 14:00:00 " + deviceName + " DENY TCP " + sourceIp + ":45122 -> " + destinationIp + ":22",
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
        flow.markSuspicious(NetFlowAnomalyType.PORT_SCAN, "scan", incidentId);
        return flow;
    }
}
