package com.aiops.backend;

import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.netflow.NetFlowImportRunResponse;
import com.aiops.backend.netflow.NetFlowImportService;
import com.aiops.backend.netflow.NetFlowImportStatus;
import com.aiops.backend.netflow.NetFlowProperties;
import com.aiops.backend.netflow.NetFlowSource;
import com.aiops.backend.netflow.NetFlowSourceRepository;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class NetFlowImportServiceIntegrationTests {

    @Autowired
    private NetFlowImportService importService;

    @Autowired
    private NetFlowSourceRepository sourceRepository;

    @Autowired
    private NetworkFlowRepository networkFlowRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private NetFlowProperties properties;

    @TempDir
    Path tempDir;

    private String originalNfdumpCommand;

    @BeforeEach
    void captureOriginalProperties() {
        originalNfdumpCommand = properties.getNfdumpCommand();
    }

    @AfterEach
    void restoreProperties() {
        properties.setNfdumpCommand(originalNfdumpCommand);
        properties.setEnabled(true);
    }

    @Test
    void missingDirectoryReturnsBadRequest() {
        Path missingDirectory = tempDir.resolve("missing-netflow-dir");
        NetFlowSource source = sourceRepository.save(new NetFlowSource("Test source", missingDirectory.toString(), 2055, true));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> importService.importLatest(source.getId()));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("NetFlow data directory is missing"));
    }

    @Test
    void missingNfdumpCommandReturnsBadRequest() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("netflow"));
        Files.writeString(directory.resolve("nfcapd.20260815"), "placeholder");
        NetFlowSource source = sourceRepository.save(new NetFlowSource("Test source", directory.toString(), 2055, true));
        properties.setNfdumpCommand("definitely-missing-nfdump-command");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> importService.importLatest(source.getId()));

        assertEquals(400, exception.getStatusCode().value());
        assertEquals("nfdump is not available in the configured runtime", exception.getReason());
    }

    @Test
    void noMatchingFlowsReturnsBadRequest() throws IOException {
        Path command = createScript("""
                #!/bin/sh
                exit 0
                """);
        Path directory = Files.createDirectory(tempDir.resolve("netflow"));
        Files.writeString(directory.resolve("nfcapd.20260815"), "placeholder");
        NetFlowSource source = sourceRepository.save(new NetFlowSource("Test source", directory.toString(), 2055, true));
        properties.setNfdumpCommand(command.toString());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> importService.importLatest(source.getId()));

        assertEquals(400, exception.getStatusCode().value());
        assertEquals("No valid NetFlow records found in nfdump output", exception.getReason());
    }

    @Test
    void repeatedImportOfSameFlowsSucceedsWithoutDuplicateIncidentsOrEvidence() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("netflow-repeat"));
        Files.writeString(directory.resolve("nfcapd.20260822"), "placeholder");
        Path command = createScript("""
                #!/bin/sh
                cat <<'EOF'
                1755860400.000,1755860401.000,10.9.8.7,10.9.8.8,40000,21,6,1,64,0.0.0.0,1,2
                1755860401.000,1755860402.000,10.9.8.7,10.9.8.8,40001,22,6,1,64,0.0.0.0,1,2
                1755860402.000,1755860403.000,10.9.8.7,10.9.8.8,40002,23,6,1,64,0.0.0.0,1,2
                1755860403.000,1755860404.000,10.9.8.7,10.9.8.8,40003,80,6,1,64,0.0.0.0,1,2
                1755860404.000,1755860405.000,10.9.8.7,10.9.8.8,40004,443,6,1,64,0.0.0.0,1,2
                1755860405.000,1755860406.000,10.9.8.7,10.9.8.8,40005,3306,6,1,64,0.0.0.0,1,2
                1755860406.000,1755860407.000,10.9.8.7,10.9.8.8,40006,5432,6,1,64,0.0.0.0,1,2
                EOF
                """);
        NetFlowSource source = sourceRepository.save(new NetFlowSource("Repeatable source", directory.toString(), 2055, true));
        properties.setNfdumpCommand(command.toString());

        NetFlowImportRunResponse first = importService.importLatest(source.getId());
        assertEquals(NetFlowImportStatus.SUCCESS, first.status());
        assertEquals(7, first.recordsRead());
        assertEquals(7, first.recordsImported());
        assertEquals(1, first.incidentsCreated());

        long flowCount = networkFlowRepository.count();
        long incidentCount = portScanIncidents("10.9.8.7", "10.9.8.8").size();
        int eventCount = portScanIncidents("10.9.8.7", "10.9.8.8").getFirst().getEventCount();

        NetFlowImportRunResponse second = assertDoesNotThrow(() -> importService.importLatest(source.getId()));
        assertEquals(NetFlowImportStatus.SUCCESS, second.status());
        assertEquals(7, second.recordsRead());
        assertEquals(0, second.recordsImported());
        assertEquals(0, second.incidentsCreated());
        assertEquals(flowCount, networkFlowRepository.count());
        assertEquals(incidentCount, portScanIncidents("10.9.8.7", "10.9.8.8").size());
        assertEquals(eventCount, portScanIncidents("10.9.8.7", "10.9.8.8").getFirst().getEventCount());
    }

    @Test
    void scheduledImportOfEmptyDirectoryDoesNotFail() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("netflow-empty-auto"));
        NetFlowSource source = sourceRepository.save(new NetFlowSource(
                "Quiet source",
                directory.toString(),
                2055,
                true
        ));
        long runsBefore = importService.listRuns().size();
        assertDoesNotThrow(() -> importService.importQuietly(source.getId()));
        assertEquals(runsBefore, importService.listRuns().size());
    }

    @Test
    void icmpEchoCsvImportsWithoutPortScan() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("netflow-icmp"));
        Files.writeString(directory.resolve("nfcapd.20260824"), "placeholder");
        Path command = createScript("""
                #!/bin/sh
                cat <<'EOF'
                1787584534.741,1787584539.742,10.0.0.1,10.0.0.10,0,0.0,1,6,504,172.30.30.10,0,0
                1787584534.741,1787584539.742,10.0.0.10,10.0.0.1,0,8.0,1,6,504,172.30.30.10,0,0
                EOF
                """);
        NetFlowSource source = sourceRepository.save(new NetFlowSource(
                "ICMP source",
                directory.toString(),
                2055,
                true
        ));
        properties.setNfdumpCommand(command.toString());

        NetFlowImportRunResponse result = importService.importLatest(source.getId());
        assertEquals(NetFlowImportStatus.SUCCESS, result.status());
        assertEquals(2, result.recordsRead());
        assertEquals(2, result.recordsImported());
        assertEquals(0, result.incidentsCreated());
        assertEquals(0, result.suspiciousFlows());

        List<NetworkFlow> imported = networkFlowRepository.findAll().stream()
                .filter(flow -> "10.0.0.10".equals(flow.getSourceIp()) || "10.0.0.10".equals(flow.getDestinationIp()))
                .filter(flow -> "ICMP".equals(flow.getProtocol()))
                .toList();
        assertEquals(2, imported.size());
        for (NetworkFlow flow : imported) {
            assertEquals("ICMP", flow.getProtocol());
            assertNull(flow.getSourcePort());
            assertNull(flow.getDestinationPort());
            assertFalse(flow.isSuspicious());
            assertNull(flow.getAnomalyType());
        }
        assertTrue(portScanIncidents("10.0.0.10", "10.0.0.1").isEmpty());
        assertTrue(portScanIncidents("10.0.0.1", "10.0.0.10").isEmpty());
    }

    private List<Incident> portScanIncidents(String sourceIp, String destinationIp) {
        String prefix = "NETFLOW:PORT_SCAN:" + sourceIp + ":" + destinationIp;
        return incidentRepository.findAll().stream()
                .filter(incident -> incident.getCorrelationKey() != null && incident.getCorrelationKey().startsWith(prefix))
                .toList();
    }

    private Path createScript(String content) throws IOException {
        Path script = tempDir.resolve("fake-nfdump.sh");
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }
}
