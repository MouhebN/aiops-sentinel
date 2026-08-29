package com.aiops.backend.netflow;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class NetFlowImportService {

    private static final String NFDUMP_CSV_FORMAT = "csv:%tsr,%ter,%sa,%da,%sp,%dp,%pr,%pkt,%byt,%ra,%in,%out";
    private static final Logger log = LoggerFactory.getLogger(NetFlowImportService.class);
    private final NetFlowSourceService sourceService;
    private final NetFlowImportRunRepository importRunRepository;
    private final NetworkFlowRepository networkFlowRepository;
    private final NetFlowRecordParser recordParser;
    private final NetFlowAnomalyDetectionService anomalyDetectionService;
    private final NetFlowProperties properties;
    private final AuditLogService auditLogService;

    public NetFlowImportService(
            NetFlowSourceService sourceService,
            NetFlowImportRunRepository importRunRepository,
            NetworkFlowRepository networkFlowRepository,
            NetFlowRecordParser recordParser,
            NetFlowAnomalyDetectionService anomalyDetectionService,
            NetFlowProperties properties,
            AuditLogService auditLogService
    ) {
        this.sourceService = sourceService;
        this.importRunRepository = importRunRepository;
        this.networkFlowRepository = networkFlowRepository;
        this.recordParser = recordParser;
        this.anomalyDetectionService = anomalyDetectionService;
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public NetFlowImportRunResponse importLatest(Long sourceId) {
        NetFlowSource source = sourceService.find(sourceId);
        if (!source.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NetFlow source is disabled");
        }
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "NetFlow import is disabled");
        }

        NetFlowImportRun run = importRunRepository.save(new NetFlowImportRun(source.getId()));
        log.info("NetFlow import started sourceId={} source={}", source.getId(), source.getName());
        auditLogService.log(
                AuditAction.NETFLOW_IMPORT_STARTED,
                "NETFLOW_SOURCE",
                source.getId().toString(),
                "Started NetFlow import for " + source.getName()
        );

        long recordsRead = 0;
        long recordsImported = 0;
        try {
            Path directory = Path.of(source.getDataDirectory());
            validateDirectory(directory);
            List<String> outputLines = executeNfdump(directory);
            List<ParsedNetFlowRecord> parsedRecords = recordParser.parse(outputLines);
            if (parsedRecords.isEmpty()) {
                throw expectedFailure("NetFlow import produced no matching flows");
            }
            recordsRead = parsedRecords.size();
            PersistResult persistResult = persistRecords(source.getId(), parsedRecords);
            List<NetworkFlow> imported = persistResult.imported();
            recordsImported = imported.size();
            long recordsSkipped = persistResult.skipped();
            if (recordsSkipped > 0) {
                log.info("[NetFlow-import] skipped duplicate evidence count={}", recordsSkipped);
            }

            NetFlowAnomalyDetectionService.DetectionResult detectionResult =
                    anomalyDetectionService.analyze(source, imported);
            run.complete(recordsRead, recordsImported, detectionResult.suspiciousFlows(), detectionResult.incidentsCreated());
            source.recordImport(NetFlowImportStatus.SUCCESS, "Imported " + recordsImported + " flow records", Instant.now());
            log.info(
                    "[NetFlow-import] import completed sourceId={} recordsRead={} recordsImported={} recordsSkipped={} suspiciousFlows={} incidentsCreated={}",
                    source.getId(),
                    recordsRead,
                    recordsImported,
                    recordsSkipped,
                    detectionResult.suspiciousFlows(),
                    detectionResult.incidentsCreated()
            );
            auditLogService.log(
                    AuditAction.NETFLOW_IMPORT_COMPLETED,
                    "NETFLOW_SOURCE",
                    source.getId().toString(),
                    "Completed NetFlow import for " + source.getName() + " with " + recordsImported + " new records"
            );
            return NetFlowImportRunResponse.from(run);
        } catch (ResponseStatusException exception) {
            run.fail(exception.getReason(), recordsRead, recordsImported);
            source.recordImport(NetFlowImportStatus.FAILED, exception.getReason(), Instant.now());
            auditLogService.log(
                    AuditAction.NETFLOW_IMPORT_FAILED,
                    "NETFLOW_SOURCE",
                    source.getId().toString(),
                    "NetFlow import failed for " + source.getName() + ": " + exception.getReason()
            );
            throw exception;
        } catch (Exception exception) {
            String message = "Unexpected server error during NetFlow import";
            log.error(
                    "NetFlow import failed unexpectedly for sourceId={} message={}",
                    source.getId(),
                    exception.getMessage(),
                    exception
            );
            run.fail(message, recordsRead, recordsImported);
            source.recordImport(NetFlowImportStatus.FAILED, message, Instant.now());
            auditLogService.log(
                AuditAction.NETFLOW_IMPORT_FAILED,
                "NETFLOW_SOURCE",
                source.getId().toString(),
                "NetFlow import failed for " + source.getName() + ": " + message
            );
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, exception);
        }
    }

    @Transactional
    public NetFlowImportRunResponse importSample() {
        NetFlowImportRun run = importRunRepository.save(new NetFlowImportRun(null));
        log.info("NetFlow import started source=demo-sample");
        List<ParsedNetFlowRecord> sampleRecords = buildSampleRecords();
        PersistResult persistResult = persistRecords(null, sampleRecords);
        List<NetworkFlow> imported = persistResult.imported();
        NetFlowAnomalyDetectionService.DetectionResult detectionResult =
                anomalyDetectionService.analyze(null, imported);
        run.complete(sampleRecords.size(), imported.size(), detectionResult.suspiciousFlows(), detectionResult.incidentsCreated());
        log.info(
                "NetFlow import completed source=demo-sample recordsRead={} recordsImported={} recordsSkipped={} suspiciousFlows={} incidentsCreated={}",
                sampleRecords.size(),
                imported.size(),
                persistResult.skipped(),
                detectionResult.suspiciousFlows(),
                detectionResult.incidentsCreated()
        );
        auditLogService.log(
                AuditAction.NETFLOW_IMPORT_COMPLETED,
                "NETFLOW_SOURCE",
                null,
                "Completed demo NetFlow sample import with " + imported.size() + " new records"
        );
        return NetFlowImportRunResponse.from(run);
    }

    @Transactional
    public void importQuietly(Long sourceId) {
        NetFlowSource source = sourceService.find(sourceId);
        if (!source.isEnabled() || !properties.isEnabled()) {
            return;
        }
        Path directory = Path.of(source.getDataDirectory());
        if (!directoryHasFiles(directory)) {
            return;
        }
        try {
            List<String> outputLines = executeNfdump(directory);
            List<ParsedNetFlowRecord> parsedRecords = recordParser.parse(outputLines);
            if (parsedRecords.isEmpty()) {
                return;
            }
            PersistResult persistResult = persistRecords(source.getId(), parsedRecords);
            List<NetworkFlow> imported = persistResult.imported();
            if (imported.isEmpty()) {
                log.debug(
                        "[NetFlow-import] scheduled skip sourceId={} read={} skipped={}",
                        source.getId(),
                        parsedRecords.size(),
                        persistResult.skipped()
                );
                return;
            }
            NetFlowAnomalyDetectionService.DetectionResult detectionResult =
                    anomalyDetectionService.analyze(source, imported);
            source.recordImport(
                    NetFlowImportStatus.SUCCESS,
                    "Imported " + imported.size() + " flow records",
                    Instant.now()
            );
            log.info(
                    "[NetFlow-import] scheduled import sourceId={} recordsRead={} recordsImported={} recordsSkipped={} suspiciousFlows={} incidentsCreated={}",
                    source.getId(),
                    parsedRecords.size(),
                    imported.size(),
                    persistResult.skipped(),
                    detectionResult.suspiciousFlows(),
                    detectionResult.incidentsCreated()
            );
            auditLogService.log(
                    AuditAction.NETFLOW_IMPORT_COMPLETED,
                    "NETFLOW_SOURCE",
                    source.getId().toString(),
                    "Automatic NetFlow import for " + source.getName() + " with " + imported.size() + " new records"
            );
        } catch (ResponseStatusException exception) {
            log.warn(
                    "Scheduled NetFlow import skipped sourceId={} reason={}",
                    sourceId,
                    exception.getReason()
            );
        }
    }

    @Transactional(readOnly = true)
    public List<NetFlowImportRunResponse> listRuns() {
        return importRunRepository.findTop20ByOrderByStartedAtDesc().stream()
                .map(NetFlowImportRunResponse::from)
                .toList();
    }

    private boolean directoryHasFiles(Path directory) {
        Path normalizedDirectory = directory.normalize();
        if (!Files.isDirectory(normalizedDirectory) || !Files.isReadable(normalizedDirectory)) {
            return false;
        }
        try (Stream<Path> files = Files.list(normalizedDirectory)) {
            return files.findAny().isPresent();
        } catch (IOException exception) {
            return false;
        }
    }

    private void validateDirectory(Path directory) {
        Path normalizedDirectory = directory.normalize();
        if (!Files.exists(normalizedDirectory)) {
            throw expectedFailure(
                    "NetFlow data directory is missing: " + normalizedDirectory
                            + ". For local dev set NETFLOW_DATA_DIR or create ./runtime/netflow. "
                            + "For Docker ensure netflow_data volume is mounted."
            );
        }

        if (!Files.isDirectory(normalizedDirectory) || !Files.isReadable(normalizedDirectory)) {
            throw inaccessibleDirectory(normalizedDirectory, null);
        }

        try {
            boolean hasFiles;
            try (Stream<Path> files = Files.list(normalizedDirectory)) {
                hasFiles = files.findAny().isPresent();
            }
            if (!hasFiles) {
                throw expectedFailure("NetFlow data directory is empty: " + normalizedDirectory);
            }
        } catch (IOException exception) {
            throw inaccessibleDirectory(normalizedDirectory, exception);
        }
    }

    private ResponseStatusException inaccessibleDirectory(Path directory, Exception cause) {
        String message = "NetFlow data directory is not accessible: " + directory
                + ". For local dev set NETFLOW_DATA_DIR or create ./runtime/netflow. "
                + "For Docker ensure netflow_data volume is mounted.";
        if (cause != null) {
            log.warn("NetFlow import directory access failed directory={} message={}", directory, cause.getMessage());
        }
        return cause == null ? expectedFailure(message) : expectedFailure(message, cause);
    }

    private List<String> executeNfdump(Path directory) {
        // nfdump -R reads the directory recursively, so Import Latest Flows decodes
        // every nfcapd file under the source data directory (full history), not only
        // files written since the previous import. Duplicate rows are skipped by recordHash;
        // this method does not checkpoint a high-water file.
        ProcessBuilder processBuilder = new ProcessBuilder(
                properties.getNfdumpCommand(),
                "-R",
                directory.toString(),
                "-o",
                NFDUMP_CSV_FORMAT
        );
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException exception) {
            log.warn(
                    "NetFlow import could not start nfdump command={} message={}",
                    properties.getNfdumpCommand(),
                    exception.getMessage()
            );
            throw expectedFailure("nfdump is not available in the configured runtime", exception);
        }
        List<String> outputLines = new ArrayList<>();
        String errorText;
        try (BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        );
             BufferedReader stderrReader = new BufferedReader(
                     new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)
             )) {
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                outputLines.add(line);
            }
            errorText = stderrReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
        } catch (IOException exception) {
            log.error("NetFlow import could not read nfdump output directory={} message={}", directory, exception.getMessage(), exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read nfdump output", exception);
        }
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn(
                        "NetFlow import nfdump failed directory={} exitCode={} stderr={}",
                        directory,
                        exitCode,
                        summarize(errorText)
                );
                if (errorText.toLowerCase(Locale.ROOT).contains("not found")) {
                    throw expectedFailure("nfdump is not available in the configured runtime");
                }
                throw expectedFailure(
                        errorText.isBlank() ? "nfdump failed to read NetFlow records" : "nfdump failed to read NetFlow records"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "nfdump execution was interrupted", exception);
        }
        log.info(
                "NetFlow import nfdump completed directory={} outputLines={} stderr={}",
                directory,
                outputLines.size(),
                summarize(errorText)
        );
        return outputLines;
    }

    private ResponseStatusException expectedFailure(String message) {
        log.warn("NetFlow import failed: {}", message);
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException expectedFailure(String message, Exception cause) {
        log.warn("NetFlow import failed: {} cause={}", message, cause.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message, cause);
    }

    private String summarize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String sanitized = text.replaceAll("\\s+", " ").trim();
        return sanitized.length() <= 300 ? sanitized : sanitized.substring(0, 300) + "...";
    }

    private PersistResult persistRecords(Long sourceId, List<ParsedNetFlowRecord> parsedRecords) {
        List<NetworkFlow> imported = new ArrayList<>();
        long skipped = 0;
        for (ParsedNetFlowRecord record : parsedRecords) {
            String recordHash = recordHash(sourceId, record);
            if (networkFlowRepository.findByRecordHash(recordHash).isPresent()) {
                skipped++;
                log.debug("[NetFlow-import] skipped duplicate evidence recordHash={}", recordHash);
                continue;
            }
            long durationMs = Math.max(0L, Duration.between(record.startTime(), record.endTime()).toMillis());
            NetworkFlow flow = new NetworkFlow(
                    sourceId,
                    record.startTime(),
                    record.endTime(),
                    durationMs,
                    record.sourceIp(),
                    record.destinationIp(),
                    record.sourcePort(),
                    record.destinationPort(),
                    record.protocol(),
                    record.packets(),
                    record.bytes(),
                    record.exporterName(),
                    record.inputInterface(),
                    record.outputInterface(),
                    recordHash,
                    record.rawRecord()
            );
            imported.add(networkFlowRepository.save(flow));
        }
        return new PersistResult(imported, skipped);
    }

    private String recordHash(Long sourceId, ParsedNetFlowRecord record) {
        String payload = String.join("|",
                String.valueOf(sourceId == null ? "" : sourceId),
                value(record.sourceIp()),
                value(record.destinationIp()),
                String.valueOf(record.sourcePort() == null ? "" : record.sourcePort()),
                String.valueOf(record.destinationPort() == null ? "" : record.destinationPort()),
                value(record.protocol()),
                record.startTime().toString(),
                record.endTime().toString(),
                String.valueOf(record.packets()),
                String.valueOf(record.bytes())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private List<ParsedNetFlowRecord> buildSampleRecords() {
        Instant base = Instant.now().minus(Duration.ofMinutes(1));
        List<ParsedNetFlowRecord> records = new ArrayList<>();
        int[] ports = {21, 22, 23, 80, 443, 3306, 5432};
        for (int index = 0; index < ports.length; index++) {
            records.add(new ParsedNetFlowRecord(
                    base.plusSeconds(index * 10L),
                    base.plusSeconds(index * 10L + 2L),
                    "192.168.1.106",
                    "192.168.1.110",
                    40000 + index,
                    ports[index],
                    "TCP",
                    12,
                    2048 + index * 512L,
                    "demo-exporter",
                    1,
                    2,
                    "sample-port-scan-" + ports[index]
            ));
        }
        for (int index = 0; index < 5; index++) {
            records.add(new ParsedNetFlowRecord(
                    base.plusSeconds(index * 15L),
                    base.plusSeconds(index * 15L + 5L),
                    "10.10.5.21",
                    "10.10.5." + (30 + index),
                    51000 + index,
                    445,
                    "TCP",
                    30,
                    4096 + index * 1024L,
                    "demo-exporter",
                    2,
                    3,
                    "sample-many-dest-" + index
            ));
        }
        records.add(new ParsedNetFlowRecord(
                base.plusSeconds(90),
                base.plusSeconds(120),
                "172.16.10.5",
                "172.16.10.20",
                53000,
                3389,
                "TCP",
                5000,
                properties.getHighVolumeThresholdBytes() + 1024L,
                "demo-exporter",
                4,
                5,
                "sample-high-volume"
        ));
        return records;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private record PersistResult(List<NetworkFlow> imported, long skipped) {
    }
}
