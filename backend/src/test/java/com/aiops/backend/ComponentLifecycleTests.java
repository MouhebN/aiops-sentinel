package com.aiops.backend;

import com.aiops.backend.component.ComponentMonitoringScheduler;
import com.aiops.backend.component.ComponentStatus;
import com.aiops.backend.component.Criticality;
import com.aiops.backend.component.MonitoredComponentResponse;
import com.aiops.backend.component.MonitoredComponentService;
import com.aiops.backend.component.MonitoringMethod;
import com.aiops.backend.component.SaveMonitoredComponentRequest;
import com.aiops.backend.component.UpdateComponentStatusRequest;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.EventIngestionRequest;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.EventService;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.topology.ComponentRelationService;
import com.aiops.backend.topology.ComponentRelationType;
import com.aiops.backend.topology.SaveComponentRelationRequest;
import com.aiops.backend.topology.TopologyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ComponentLifecycleTests {

    @Autowired
    private MonitoredComponentService componentService;

    @Autowired
    private ComponentMonitoringScheduler scheduler;

    @Autowired
    private ComponentRelationService relationService;

    @Autowired
    private TopologyService topologyService;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Test
    void stopMonitoringPersistsAndKeepsOperationalStatus() {
        MonitoredComponentResponse created = createCamera("CAM-LIFE-1");
        componentService.updateStatus(created.id(), new UpdateComponentStatusRequest(
                ComponentStatus.UP,
                Instant.parse("2026-08-23T12:00:00Z"),
                Instant.parse("2026-08-23T12:00:00Z"),
                null
        ));

        MonitoredComponentResponse stopped = componentService.disable(created.id());

        assertThat(stopped.enabled()).isFalse();
        assertThat(stopped.lastStatus()).isEqualTo(ComponentStatus.UP);
        assertThat(stopped.lastStatus()).isNotEqualTo(ComponentStatus.DOWN);
        assertThat(componentService.get(created.id()).enabled()).isFalse();
        assertThat(componentService.listEnabled()).noneMatch(item -> item.id().equals(created.id()));
        assertThat(topologyService.getTopology().nodes())
                .anyMatch(node -> created.id().equals(node.componentId())
                        && !node.monitoringEnabled()
                        && node.operationalStatus() == ComponentStatus.UP);
    }

    @Test
    void schedulerIgnoresStoppedComponentAndDoesNotRestartIt() {
        MonitoredComponentResponse created = createCamera("CAM-LIFE-2");
        Instant checkedAt = Instant.parse("2026-08-23T12:00:00Z");
        componentService.updateStatus(created.id(), new UpdateComponentStatusRequest(
                ComponentStatus.UP,
                checkedAt,
                checkedAt,
                null
        ));
        componentService.disable(created.id());

        scheduler.checkEnabledComponents();

        MonitoredComponentResponse afterCycle = componentService.get(created.id());
        assertThat(afterCycle.enabled()).isFalse();
        assertThat(afterCycle.lastStatus()).isEqualTo(ComponentStatus.UP);
        assertThat(afterCycle.lastCheckedAt()).isEqualTo(checkedAt);
    }

    @Test
    void startMonitoringPersistsTrueAndMakesCheckDue() {
        MonitoredComponentResponse created = createCamera("CAM-LIFE-3");
        componentService.disable(created.id());
        MonitoredComponentResponse started = componentService.enable(created.id());

        assertThat(started.enabled()).isTrue();
        assertThat(started.lastCheckedAt()).isNull();
        assertThat(componentService.listEnabled()).anyMatch(item -> item.id().equals(created.id()));
    }

    @Test
    void collectorStatusUpdateDoesNotWriteWhileStopped() {
        MonitoredComponentResponse created = createCamera("CAM-LIFE-4");
        Instant checkedAt = Instant.parse("2026-08-23T12:10:00Z");
        componentService.updateStatus(created.id(), new UpdateComponentStatusRequest(
                ComponentStatus.UP,
                checkedAt,
                checkedAt,
                null
        ));
        componentService.disable(created.id());

        componentService.updateStatus(created.id(), new UpdateComponentStatusRequest(
                ComponentStatus.DOWN,
                Instant.parse("2026-08-23T12:11:00Z"),
                null,
                "stale collector"
        ));

        MonitoredComponentResponse after = componentService.get(created.id());
        assertThat(after.enabled()).isFalse();
        assertThat(after.lastStatus()).isEqualTo(ComponentStatus.UP);
        assertThat(after.lastCheckedAt()).isEqualTo(checkedAt);
        assertThat(after.lastError()).isNull();
    }

    @Test
    void checkNowDoesNotReEnableMonitoring() {
        MonitoredComponentResponse created = createCamera("CAM-LIFE-5");
        componentService.disable(created.id());

        componentService.checkNow(created.id());

        MonitoredComponentResponse after = componentService.get(created.id());
        assertThat(after.enabled()).isFalse();
    }

    @Test
    void updateDoesNotDefaultStoppedComponentBackToEnabled() {
        MonitoredComponentResponse created = createCamera("CAM-LIFE-6");
        componentService.disable(created.id());

        MonitoredComponentResponse updated = componentService.update(created.id(), request(
                created.name(),
                created.ipAddress(),
                null
        ));

        assertThat(updated.enabled()).isFalse();
        updated = componentService.update(created.id(), request(created.name(), created.ipAddress(), false));
        assertThat(updated.enabled()).isFalse();
    }

    @Test
    void deleteRemovesRelationsAndTopologyNodeButKeepsEvents() {
        MonitoredComponentResponse camera = createCamera("CAM-LIFE-7");
        MonitoredComponentResponse switchNode = componentService.create(request(
                "SW-LIFE-7",
                "10.10.10.2",
                true
        ));
        relationService.create(new SaveComponentRelationRequest(
                switchNode.id(),
                camera.id(),
                ComponentRelationType.CONNECTED_TO,
                null
        ));
        eventService.ingest(new EventIngestionRequest(
                "component-" + camera.id(),
                camera.name(),
                DeviceType.IP_CAMERA,
                "lab",
                "DEVICE_UNREACHABLE",
                Severity.WARNING,
                camera.name() + " was unreachable",
                "historical ping failure",
                Instant.parse("2026-08-23T11:00:00Z")
        ));
        String deviceId = "component-" + camera.id();
        long eventCount = eventRepository.findAll().stream()
                .filter(event -> deviceId.equals(event.getDeviceId()))
                .count();
        long incidentCount = incidentRepository.findAll().stream()
                .filter(incident -> deviceId.equals(incident.getDeviceId()))
                .count();
        assertThat(eventCount).isPositive();
        assertThat(incidentCount).isPositive();

        componentService.delete(camera.id());

        assertThatThrownBy(() -> componentService.get(camera.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(componentService.listEnabled()).noneMatch(item -> item.id().equals(camera.id()));
        assertThat(topologyService.getTopology().nodes()).noneMatch(node -> camera.id().equals(node.componentId()));
        assertThat(topologyService.getTopology().links()).noneMatch(link ->
                link.target().equals("component:" + camera.id()) || link.source().equals("component:" + camera.id())
        );
        assertThat(eventRepository.findAll().stream()
                .filter(event -> deviceId.equals(event.getDeviceId()))
                .count()).isEqualTo(eventCount);
        assertThat(incidentRepository.findAll().stream()
                .filter(incident -> deviceId.equals(incident.getDeviceId()))
                .count()).isEqualTo(incidentCount);
    }

    @Test
    void stopStartDoesNotCreateDuplicateEnabledRows() {
        MonitoredComponentResponse created = createCamera("CAM-LIFE-8");
        componentService.disable(created.id());
        componentService.enable(created.id());
        componentService.disable(created.id());
        componentService.enable(created.id());

        assertThat(componentService.list().stream().filter(item -> item.id().equals(created.id()))).hasSize(1);
        assertThat(componentService.listEnabled().stream().filter(item -> item.id().equals(created.id()))).hasSize(1);
    }

    @Test
    void snmpCommunityIsPersistedAndReturned() {
        MonitoredComponentResponse created = componentService.create(new SaveMonitoredComponentRequest(
                "CORE-SW-SNMP-" + UUID.randomUUID().toString().substring(0, 8),
                DeviceType.SWITCH,
                "172.30.30.12",
                null,
                null,
                161,
                "banklab",
                "1.3.6.1.2.1.1.1.0",
                "lab",
                Criticality.HIGH,
                Set.of(MonitoringMethod.SNMP_BASIC, MonitoringMethod.SNMP_ROUTER_METRICS),
                30,
                true
        ));

        assertThat(created.snmpCommunity()).isEqualTo("banklab");
        assertThat(componentService.get(created.id()).snmpCommunity()).isEqualTo("banklab");
    }

    @Test
    void updateDoesNotDropSnmpCommunityWhenOmitted() {
        MonitoredComponentResponse created = componentService.create(new SaveMonitoredComponentRequest(
                "CORE-RTR-SNMP-" + UUID.randomUUID().toString().substring(0, 8),
                DeviceType.ROUTER,
                "172.30.30.11",
                null,
                null,
                161,
                "banklab",
                "1.3.6.1.2.1.1.1.0",
                "lab",
                Criticality.HIGH,
                Set.of(MonitoringMethod.SNMP_ROUTER_METRICS),
                30,
                true
        ));

        MonitoredComponentResponse updated = componentService.update(created.id(), new SaveMonitoredComponentRequest(
                created.name(),
                created.type(),
                created.ipAddress(),
                created.httpUrl(),
                created.tcpPort(),
                created.snmpPort(),
                null,
                created.snmpOid(),
                created.location(),
                created.criticality(),
                created.monitoringMethods(),
                created.checkIntervalSeconds(),
                created.enabled()
        ));

        assertThat(updated.snmpCommunity()).isEqualTo("banklab");
        assertThat(componentService.get(created.id()).snmpCommunity()).isEqualTo("banklab");
    }

    private MonitoredComponentResponse createCamera(String name) {
        return componentService.create(request(name, "10.10.10." + (40 + Math.abs(name.hashCode() % 50)), true));
    }

    private SaveMonitoredComponentRequest request(String name, String ip, Boolean enabled) {
        return new SaveMonitoredComponentRequest(
                name + "-" + UUID.randomUUID().toString().substring(0, 8),
                DeviceType.IP_CAMERA,
                ip,
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
    }
}
