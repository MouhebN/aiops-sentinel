package com.aiops.backend;

import com.aiops.backend.device.DeviceRepository;
import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.component.ComponentStatus;
import com.aiops.backend.component.Criticality;
import com.aiops.backend.component.MonitoredComponentService;
import com.aiops.backend.component.MonitoringMethod;
import com.aiops.backend.component.SaveMonitoredComponentRequest;
import com.aiops.backend.component.UpdateComponentStatusRequest;
import com.aiops.backend.event.EventIngestionRequest;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.EventService;
import com.aiops.backend.event.Severity;
import com.aiops.backend.report.DiagnosticReportService;
import com.aiops.backend.report.SaveDiagnosticReportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private EventService eventService;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private DeviceRepository deviceRepository;

	@Autowired
	private DiagnosticReportService diagnosticReportService;

	@Autowired
	private MonitoredComponentService monitoredComponentService;

	@Test
	void contextLoads() {
	}

	@Test
	void ingestingCriticalEventStoresEventAndMarksDeviceDown() {
		var event = eventService.ingest(new EventIngestionRequest(
				"ups-01",
				"UPS Main Rack",
				DeviceType.UPS,
				"Server Room",
				"UPS_BATTERY_LOW",
				Severity.CRITICAL,
				"UPS battery below 15%",
				"battery=12",
				Instant.parse("2026-05-29T10:00:00Z")
		));

		assertThat(eventRepository.findById(event.getId())).isPresent();
		assertThat(deviceRepository.findById("ups-01"))
				.isPresent()
				.get()
				.extracting(device -> device.getStatus())
				.isEqualTo(DeviceStatus.DOWN);
	}

	@Test
	void eventCodeCanBeAutoClassifiedFromUnderscoreName() {
		var event = eventService.ingest(new EventIngestionRequest(
				"camera-01",
				"Main Entrance Camera",
				DeviceType.IP_CAMERA,
				"Entrance",
				"CAMERA_OFFLINE",
				null,
				"Camera is offline",
				null,
				Instant.parse("2026-05-29T11:00:00Z")
		));

		assertThat(event.getSeverity()).isEqualTo(Severity.CRITICAL);
		assertThat(deviceRepository.findById("camera-01"))
				.isPresent()
				.get()
				.extracting(device -> device.getStatus())
				.isEqualTo(DeviceStatus.DOWN);
	}

	@Test
	void diagnosticReportCanBeSavedAndListed() {
		var report = diagnosticReportService.save(new SaveDiagnosticReportRequest(
				1L,
				"ups-01",
				"UPS Main Rack",
				DeviceType.UPS,
				"Server Room",
				"UPS_BATTERY_LOW",
				Severity.CRITICAL,
				"UPS battery below 15%",
				"battery=12,runtime_minutes=4",
				Instant.parse("2026-05-29T12:00:00Z"),
				"UPS Main Rack reported a low battery condition.",
				"P1",
				"Protected servers may lose backup power.",
				"Protected devices may shut down if power fails.",
				List.of("UPS battery is below safe runtime"),
				List.of("Check UPS battery level", "Verify input power"),
				List.of("ping <ups-ip>", "snmpwalk -v2c -c <community> <ups-ip> 1.3.6.1.2.1.33"),
				List.of(),
				List.of(),
				"rules",
				null
		));

		assertThat(report.id()).isNotNull();
		assertThat(diagnosticReportService.list())
				.extracting(savedReport -> savedReport.id())
				.contains(report.id());
		assertThat(diagnosticReportService.get(report.id()).suggestedActions())
				.contains("Check UPS battery level");
		assertThat(diagnosticReportService.get(report.id()).probableCauses())
				.contains("UPS battery is below safe runtime");
	}

	@Test
	void monitoredComponentCanBeCreatedEnabledAndUpdatedByCollector() {
		var component = monitoredComponentService.create(new SaveMonitoredComponentRequest(
				"Home Wi-Fi Router",
				DeviceType.ROUTER,
				"192.168.1.1",
				"http://192.168.1.1",
				80,
				161,
				"public",
				"1.3.6.1.2.1.1.1.0",
				"Home Lab",
				Criticality.HIGH,
				Set.of(MonitoringMethod.PING, MonitoringMethod.HTTP_HEALTH),
				null,
				true
		));

		assertThat(component.id()).isNotNull();
		assertThat(component.checkIntervalSeconds()).isEqualTo(30);
		assertThat(monitoredComponentService.listEnabled())
				.extracting(enabledComponent -> enabledComponent.id())
				.contains(component.id());

		var status = monitoredComponentService.updateStatus(component.id(), new UpdateComponentStatusRequest(
				ComponentStatus.DOWN,
				Instant.parse("2026-05-29T13:00:00Z"),
				null,
				"ping 192.168.1.1 failed"
		));

		assertThat(status.lastStatus()).isEqualTo(ComponentStatus.DOWN);
		assertThat(status.failureCount()).isEqualTo(1);
		assertThat(status.lastError()).contains("ping 192.168.1.1 failed");
	}

}
