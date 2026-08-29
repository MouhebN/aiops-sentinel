package com.aiops.backend.component;

import com.aiops.backend.event.EventIngestionRequest;
import com.aiops.backend.event.EventService;
import com.aiops.backend.event.Severity;
import com.aiops.backend.metric.MetricSampleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ComponentCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentCheckService.class);
    private static final Duration PING_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final EventService eventService;
    private final MetricSampleService metricSampleService;
    private final HttpClient httpClient;

    public ComponentCheckService(EventService eventService, MetricSampleService metricSampleService) {
        this.eventService = eventService;
        this.metricSampleService = metricSampleService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    @Transactional
    public void checkComponent(MonitoredComponent component, Instant checkedAt) {
        if (!component.isEnabled()) {
            LOGGER.debug(
                    "Skipping scheduled check for stopped component: id={} name=\"{}\"",
                    component.getId(),
                    component.getName()
            );
            return;
        }
        ComponentStatus previousStatus = component.getLastStatus();
        List<CheckResult> results = runChecks(component);

        ComponentStatus currentStatus = resolveStatus(results);
        String error = results.stream()
                .filter(result -> result.status() == ComponentStatus.DOWN || result.status() == ComponentStatus.DEGRADED)
                .findFirst()
                .map(CheckResult::rawLog)
                .orElse(null);
        String checkDetails = summarizeCheckResults(results);

        component.updateStatus(
                currentStatus,
                checkedAt,
                seenAt(results, currentStatus, checkedAt),
                error,
                checkDetails
        );
        saveMetricSamples(component, results, checkedAt);
        LOGGER.info(
                "Component check completed: id={} name=\"{}\" previous={} current={} methods={} error={}",
                component.getId(),
                component.getName(),
                previousStatus,
                currentStatus,
                component.getMonitoringMethods(),
                error == null ? "none" : error
        );

        if (shouldEmitEvent(previousStatus, currentStatus)) {
            CheckResult eventResult = selectEventResult(results, currentStatus);
            LOGGER.info(
                    "Component status changed: id={} name=\"{}\" eventType={} severity={}",
                    component.getId(),
                    component.getName(),
                    eventResult.eventType(),
                    eventResult.severity()
            );
            eventService.ingest(new EventIngestionRequest(
                    componentDeviceId(component),
                    component.getName(),
                    component.getType(),
                    component.getLocation(),
                    eventResult.eventType(),
                    eventResult.severity(),
                    eventResult.message(),
                    eventResult.rawLog(),
                    checkedAt
            ));
        }
    }

    public ComponentConnectionCheckResponse testConnection(MonitoredComponent component, Instant checkedAt) {
        List<CheckResult> results = runChecks(component);
        ComponentStatus resultStatus = resolveStatus(results);
        boolean successful = resultStatus == ComponentStatus.UP || resultStatus == ComponentStatus.WARNING;
        String message = connectionTestMessage(component.getName(), resultStatus);
        List<String> details = results.stream()
                .map(result -> result.method() + ": " + result.rawLog())
                .toList();
        String error = results.stream()
                .filter(result -> result.status() == ComponentStatus.DOWN || result.status() == ComponentStatus.DEGRADED)
                .findFirst()
                .map(CheckResult::rawLog)
                .orElse(null);

        LOGGER.info(
                "Component connection test completed: id={} name=\"{}\" result={} methods={} monitoringEnabled={}",
                component.getId(),
                component.getName(),
                resultStatus,
                component.getMonitoringMethods(),
                component.isEnabled()
        );

        Instant seenAt = seenAt(results, resultStatus, checkedAt);
        component.updateStatus(
                resultStatus,
                checkedAt,
                seenAt != null ? seenAt : component.getLastSeenAt(),
                error,
                summarizeCheckResults(results)
        );

        return new ComponentConnectionCheckResponse(
                component.getId(),
                component.getName(),
                successful,
                resultStatus,
                message,
                details,
                checkedAt
        );
    }

    private boolean shouldEmitEvent(ComponentStatus previousStatus, ComponentStatus currentStatus) {
        return previousStatus != currentStatus
                && (currentStatus == ComponentStatus.DOWN
                || currentStatus == ComponentStatus.DEGRADED
                || currentStatus == ComponentStatus.UP);
    }

    private CheckResult selectEventResult(List<CheckResult> results, ComponentStatus currentStatus) {
        if (currentStatus == ComponentStatus.DOWN) {
            return results.stream()
                    .filter(result -> result.status() == ComponentStatus.DOWN)
                    .findFirst()
                    .orElse(results.getFirst());
        }
        if (currentStatus == ComponentStatus.DEGRADED) {
            return results.stream()
                    .filter(result -> result.status() == ComponentStatus.DEGRADED)
                    .findFirst()
                    .orElseGet(() -> results.stream()
                            .filter(result -> result.status() == ComponentStatus.DOWN)
                            .findFirst()
                            .orElse(results.getFirst()));
        }
        return results.stream()
                .filter(result -> result.method() == MonitoringMethod.SNMP_ROUTER_METRICS)
                .findFirst()
                .orElseGet(() -> results.stream()
                .filter(result -> result.status() == ComponentStatus.UP)
                .findFirst()
                .orElse(results.getFirst()));
    }

    private ComponentStatus resolveStatus(List<CheckResult> results) {
        return ComponentStatusAggregator.aggregate(
                results.stream().map(CheckResult::status).toList()
        );
    }

    private Instant seenAt(List<CheckResult> results, ComponentStatus aggregated, Instant checkedAt) {
        boolean reachable = results.stream().anyMatch(result ->
                result.status() == ComponentStatus.UP || result.status() == ComponentStatus.WARNING);
        if (aggregated == ComponentStatus.UP || reachable) {
            return checkedAt;
        }
        return null;
    }

    private String connectionTestMessage(String name, ComponentStatus status) {
        return switch (status) {
            case UP, WARNING -> name + " connection test succeeded";
            case DEGRADED -> name + " connection test completed with mixed check results";
            default -> name + " connection test failed";
        };
    }

    private String summarizeCheckResults(List<CheckResult> results) {
        String details = String.join("\n", results.stream()
                .map(result -> result.method() + " [" + result.status() + "]: " + result.rawLog())
                .toList());
        if (details.length() <= 8000) {
            return details;
        }
        return details.substring(0, 7997) + "...";
    }

    private void saveMetricSamples(MonitoredComponent component, List<CheckResult> results, Instant checkedAt) {
        for (CheckResult result : results) {
            metricSampleService.saveSamples(
                    component.getId(),
                    component.getName(),
                    component.getType(),
                    component.getLocation(),
                    result.method().name(),
                    result.rawLog(),
                    checkedAt
            );
        }
    }

    private List<CheckResult> runChecks(MonitoredComponent component) {
        List<CheckResult> results = new ArrayList<>();

        if (component.getMonitoringMethods().contains(MonitoringMethod.PING)) {
            results.add(checkPing(component));
        }
        if (component.getMonitoringMethods().contains(MonitoringMethod.HTTP_HEALTH)) {
            results.add(checkHttp(component));
        }
        if (component.getMonitoringMethods().contains(MonitoringMethod.UPS_HTTP_METRICS)) {
            results.add(checkUpsHttpMetrics(component));
        }
        if (component.getMonitoringMethods().contains(MonitoringMethod.TCP_PORT)) {
            results.add(checkTcpPort(component));
        }
        if (component.getMonitoringMethods().contains(MonitoringMethod.RTSP_HEALTH)) {
            results.add(checkRtspHealth(component));
        }
        if (component.getMonitoringMethods().contains(MonitoringMethod.SNMP_BASIC)) {
            results.add(checkSnmp(component));
        }
        if (component.getMonitoringMethods().contains(MonitoringMethod.SNMP_ROUTER_METRICS)) {
            results.add(checkSnmpRouterMetrics(component));
        }
        if (component.getMonitoringMethods().contains(MonitoringMethod.SNMP_SERVER_METRICS)) {
            results.add(checkSnmpServerMetrics(component));
        }
        if (component.getMonitoringMethods().contains(MonitoringMethod.UPS_SNMP_METRICS)) {
            results.add(checkUpsSnmpMetrics(component));
        }

        return results;
    }

    private CheckResult checkPing(MonitoredComponent component) {
        try {
            boolean reachable = InetAddress.getByName(component.getIpAddress())
                    .isReachable((int) PING_TIMEOUT.toMillis());
            if (reachable) {
                return new CheckResult(
                        MonitoringMethod.PING,
                        ComponentStatus.UP,
                        "DEVICE_RECOVERED",
                        Severity.INFO,
                        component.getName() + " is reachable by ICMP ping",
                        "ping " + component.getIpAddress() + " succeeded"
                );
            }
            return pingFailure(component, "ping " + component.getIpAddress() + " failed: host unreachable");
        } catch (IOException exception) {
            return pingFailure(component, "ping " + component.getIpAddress() + " failed: " + exception.getMessage());
        }
    }

    private CheckResult pingFailure(MonitoredComponent component, String rawLog) {
        return new CheckResult(
                MonitoringMethod.PING,
                ComponentStatus.DOWN,
                "DEVICE_UNREACHABLE",
                severityFor(component),
                component.getName() + " is unreachable by ICMP ping",
                rawLog
        );
    }

    private CheckResult checkHttp(MonitoredComponent component) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(component.getHttpUrl()))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                return new CheckResult(
                        MonitoringMethod.HTTP_HEALTH,
                        ComponentStatus.UP,
                        "HTTP_HEALTH_RECOVERED",
                        Severity.INFO,
                        component.getName() + " HTTP health endpoint is responding",
                        "GET " + component.getHttpUrl() + " returned HTTP " + response.statusCode()
                );
            }
            return httpFailure(
                    component,
                    component.getName() + " HTTP health endpoint returned HTTP " + response.statusCode(),
                    "GET " + component.getHttpUrl() + " returned HTTP " + response.statusCode()
            );
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return httpFailure(
                    component,
                    component.getName() + " HTTP health endpoint is unreachable",
                    "GET " + component.getHttpUrl() + " failed: " + exception.getMessage()
            );
        }
    }

    private CheckResult httpFailure(MonitoredComponent component, String message, String rawLog) {
        return new CheckResult(
                MonitoringMethod.HTTP_HEALTH,
                ComponentStatus.DOWN,
                "HTTP_HEALTH_FAILED",
                severityFor(component),
                message,
                rawLog
        );
    }

    private CheckResult checkUpsHttpMetrics(MonitoredComponent component) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(component.getHttpUrl()))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                return new CheckResult(
                        MonitoringMethod.UPS_HTTP_METRICS,
                        ComponentStatus.DOWN,
                        "UPS_METRICS_UNREACHABLE",
                        severityFor(component),
                        component.getName() + " UPS metrics endpoint is not healthy",
                        "GET " + component.getHttpUrl() + " returned HTTP " + response.statusCode()
                );
            }

            String body = response.body();
            String status = jsonText(body, "status", "UNKNOWN");
            String powerSource = jsonText(body, "powerSource", "UNKNOWN");
            int batteryPercent = jsonInt(body, "batteryPercent", -1);
            int runtimeMinutes = jsonInt(body, "runtimeMinutes", -1);
            int loadPercent = jsonInt(body, "loadPercent", -1);
            int inputVoltage = jsonInt(body, "inputVoltage", -1);
            int outputVoltage = jsonInt(body, "outputVoltage", -1);
            String protectedAssets = jsonText(body, "protectedAssets", "unknown");

            List<String> issues = new ArrayList<>();
            String eventType = "UPS_METRICS_NORMAL";
            Severity severity = Severity.INFO;
            ComponentStatus resultStatus = ComponentStatus.UP;

            if ("DOWN".equalsIgnoreCase(status) || "FAULT".equalsIgnoreCase(status)) {
                issues.add("UPS status is " + status);
                eventType = "UPS_FAILURE";
                severity = severityFor(component);
                resultStatus = ComponentStatus.DOWN;
            }
            if ("BATTERY".equalsIgnoreCase(powerSource) || "ON_BATTERY".equalsIgnoreCase(status)) {
                issues.add("UPS is running on battery power");
                eventType = "UPS_ON_BATTERY";
                severity = Severity.WARNING;
                resultStatus = maxStatus(resultStatus, ComponentStatus.DEGRADED);
            }
            if (batteryPercent >= 0 && batteryPercent <= 15) {
                issues.add("UPS battery is low: " + batteryPercent + "%");
                eventType = "UPS_BATTERY_LOW";
                severity = Severity.CRITICAL;
                resultStatus = maxStatus(resultStatus, ComponentStatus.DEGRADED);
            }
            if (runtimeMinutes >= 0 && runtimeMinutes <= 10) {
                issues.add("UPS runtime is low: " + runtimeMinutes + " minutes");
                eventType = "UPS_RUNTIME_LOW";
                severity = Severity.CRITICAL;
                resultStatus = maxStatus(resultStatus, ComponentStatus.DEGRADED);
            }
            if (loadPercent >= 85) {
                issues.add("UPS load is high: " + loadPercent + "%");
                eventType = "UPS_LOAD_HIGH";
                severity = severity == Severity.CRITICAL ? Severity.CRITICAL : Severity.WARNING;
                resultStatus = maxStatus(resultStatus, ComponentStatus.DEGRADED);
            }

            String rawLog = "UPS metrics: status=" + status
                    + ", powerSource=" + powerSource
                    + ", batteryPercent=" + valueOrUnavailable(batteryPercent)
                    + ", runtimeMinutes=" + valueOrUnavailable(runtimeMinutes)
                    + ", loadPercent=" + valueOrUnavailable(loadPercent)
                    + ", inputVoltage=" + valueOrUnavailable(inputVoltage)
                    + ", outputVoltage=" + valueOrUnavailable(outputVoltage)
                    + ", protectedAssets=" + protectedAssets;

            if (!issues.isEmpty()) {
                return new CheckResult(
                        MonitoringMethod.UPS_HTTP_METRICS,
                        resultStatus,
                        eventType,
                        severity,
                        component.getName() + " UPS metrics show power protection risk",
                        rawLog + ", issues=[" + String.join("; ", issues) + "]"
                );
            }

            return new CheckResult(
                    MonitoringMethod.UPS_HTTP_METRICS,
                    ComponentStatus.UP,
                    eventType,
                    Severity.INFO,
                    component.getName() + " UPS metrics are normal",
                    rawLog
            );
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new CheckResult(
                    MonitoringMethod.UPS_HTTP_METRICS,
                    ComponentStatus.DOWN,
                    "UPS_METRICS_UNREACHABLE",
                    severityFor(component),
                    component.getName() + " UPS metrics endpoint is unreachable",
                    "GET " + component.getHttpUrl() + " failed: " + exception.getMessage()
            );
        }
    }

    private String jsonText(String json, String field, String defaultValue) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }

    private int jsonInt(String json, String field, int defaultValue) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+)")
                .matcher(json);
        if (!matcher.find()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private ComponentStatus maxStatus(ComponentStatus current, ComponentStatus candidate) {
        if (current == ComponentStatus.DOWN || candidate == ComponentStatus.DOWN) {
            return ComponentStatus.DOWN;
        }
        if (current == ComponentStatus.DEGRADED || candidate == ComponentStatus.DEGRADED) {
            return ComponentStatus.DEGRADED;
        }
        return current;
    }

    private CheckResult checkTcpPort(MonitoredComponent component) {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(component.getIpAddress(), component.getTcpPort()),
                    (int) PING_TIMEOUT.toMillis()
            );
            return new CheckResult(
                    MonitoringMethod.TCP_PORT,
                    ComponentStatus.UP,
                    "TCP_PORT_RECOVERED",
                    Severity.INFO,
                    component.getName() + " TCP port " + component.getTcpPort() + " is reachable",
                    "tcp connect " + component.getIpAddress() + ":" + component.getTcpPort() + " succeeded"
            );
        } catch (IOException exception) {
            return new CheckResult(
                    MonitoringMethod.TCP_PORT,
                    ComponentStatus.DOWN,
                    "TCP_PORT_UNREACHABLE",
                    severityFor(component),
                    component.getName() + " TCP port " + component.getTcpPort() + " is unreachable",
                    "tcp connect " + component.getIpAddress() + ":" + component.getTcpPort() + " failed: " + exception.getMessage()
            );
        }
    }

    private CheckResult checkRtspHealth(MonitoredComponent component) {
        int port = component.getTcpPort() == null ? 554 : component.getTcpPort();
        String target = "rtsp://" + component.getIpAddress() + ":" + port + "/stream";
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(component.getIpAddress(), port), (int) PING_TIMEOUT.toMillis());
            socket.setSoTimeout((int) PING_TIMEOUT.toMillis());
            String request = "OPTIONS " + target + " RTSP/1.0\r\n"
                    + "CSeq: 1\r\n"
                    + "User-Agent: AIOps-Sentinel\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            byte[] buffer = new byte[512];
            int read = socket.getInputStream().read(buffer);
            String response = read > 0
                    ? new String(buffer, 0, read, StandardCharsets.UTF_8).replace("\r", "").replace("\n", " ").trim()
                    : "";

            if (response.startsWith("RTSP/")) {
                return new CheckResult(
                        MonitoringMethod.RTSP_HEALTH,
                        ComponentStatus.UP,
                        "RTSP_STREAM_RECOVERED",
                        Severity.INFO,
                        component.getName() + " RTSP service is responding",
                        "RTSP OPTIONS " + target + " succeeded: " + response
                );
            }

            return rtspFailure(
                    component,
                    port,
                    "RTSP service accepted TCP connection but did not return a valid RTSP response: " + response
            );
        } catch (SocketTimeoutException exception) {
            return rtspFailure(component, port, "RTSP request timed out");
        } catch (IOException | IllegalArgumentException exception) {
            return rtspFailure(component, port, exception.getMessage());
        }
    }

    private CheckResult rtspFailure(MonitoredComponent component, int port, String reason) {
        return new CheckResult(
                MonitoringMethod.RTSP_HEALTH,
                ComponentStatus.DEGRADED,
                "RTSP_STREAM_DOWN",
                severityFor(component),
                component.getName() + " RTSP stream health check failed",
                "RTSP OPTIONS rtsp://" + component.getIpAddress() + ":" + port + "/stream failed: " + reason
        );
    }

    private CheckResult checkSnmp(MonitoredComponent component) {
        int port = component.getSnmpPort() == null ? 161 : component.getSnmpPort();
        String community = component.getSnmpCommunity();
        String oid = component.getSnmpOid() == null ? "1.3.6.1.2.1.1.1.0" : component.getSnmpOid();

        try {
            SnmpDecodedValue decodedValue = snmpGet(component, port, community, oid);
            return new CheckResult(
                    MonitoringMethod.SNMP_BASIC,
                    ComponentStatus.UP,
                    "SNMP_CHECK_RECOVERED",
                    Severity.INFO,
                    component.getName() + " responded to SNMP basic check",
                    "snmpget -v2c -c " + SnmpV2cGetEncoder.maskCommunity(community) + " "
                            + component.getIpAddress() + ":" + port + " " + oid
                            + " succeeded: " + decodedValue.oid() + " = " + decodedValue.value()
            );
        } catch (SocketTimeoutException exception) {
            return snmpFailure(component, port, oid, "SNMP request timed out");
        } catch (IOException | IllegalArgumentException exception) {
            return snmpFailure(component, port, oid, exception.getMessage());
        }
    }

    private CheckResult checkSnmpRouterMetrics(MonitoredComponent component) {
        int port = component.getSnmpPort() == null ? 161 : component.getSnmpPort();
        String community = component.getSnmpCommunity();

        try {
            SnmpDecodedValue uptime = snmpGet(component, port, community, "1.3.6.1.2.1.1.3.0");
            OptionalLong interfaceCountValue = readSnmpNumberSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.2.1.0"
            );
            int interfaceCount = (int) interfaceCountValue.orElse(0);
            int interfacesToCheck = Math.min(Math.max(interfaceCount, 0), 8);

            List<String> summaries = new ArrayList<>();
            List<String> issues = new ArrayList<>();
            long totalErrors = 0;

            for (int index = 1; index <= interfacesToCheck; index++) {
                String description = readSnmpTextSafely(component, port, community, "1.3.6.1.2.1.2.2.1.2." + index)
                        .orElse("ifIndex " + index);
                OptionalLong status = readSnmpNumberSafely(component, port, community, "1.3.6.1.2.1.2.2.1.8." + index);
                OptionalLong inErrors = readSnmpNumberSafely(component, port, community, "1.3.6.1.2.1.2.2.1.14." + index);
                OptionalLong outErrors = readSnmpNumberSafely(component, port, community, "1.3.6.1.2.1.2.2.1.20." + index);
                OptionalLong inOctets = readSnmpNumberSafely(component, port, community, "1.3.6.1.2.1.2.2.1.10." + index);
                OptionalLong outOctets = readSnmpNumberSafely(component, port, community, "1.3.6.1.2.1.2.2.1.16." + index);

                long interfaceErrors = inErrors.orElse(0) + outErrors.orElse(0);
                totalErrors += interfaceErrors;
                String state = status.isPresent() ? interfaceStatusName(status.getAsLong()) : "unknown";
                summaries.add(description
                        + " status=" + state
                        + " inErrors=" + inErrors.orElse(0)
                        + " outErrors=" + outErrors.orElse(0)
                        + " inOctets=" + inOctets.orElse(0)
                        + " outOctets=" + outOctets.orElse(0));

                if (status.isPresent() && status.getAsLong() != 1 && status.getAsLong() != 5) {
                    issues.add(description + " is " + state);
                }
                if (interfaceErrors > 0) {
                    issues.add(description + " has " + interfaceErrors + " interface errors");
                }
            }

            String rawLog = "SNMP router metrics: uptime=" + uptime.value()
                    + ", ifNumber=" + (interfaceCountValue.isPresent() ? interfaceCount : "unavailable")
                    + ", checkedInterfaces=" + interfacesToCheck
                    + ", totalInterfaceErrors=" + totalErrors
                    + ", interfaces=[" + String.join("; ", summaries) + "]";

            if (!issues.isEmpty()) {
                return new CheckResult(
                        MonitoringMethod.SNMP_ROUTER_METRICS,
                        ComponentStatus.DEGRADED,
                        "SNMP_ROUTER_METRICS_DEGRADED",
                        severityFor(component),
                        component.getName() + " SNMP metrics show degraded router/interface health",
                        rawLog + ", issues=[" + String.join("; ", issues) + "]"
                );
            }

            return new CheckResult(
                    MonitoringMethod.SNMP_ROUTER_METRICS,
                    ComponentStatus.UP,
                    "SNMP_ROUTER_METRICS_NORMAL",
                    Severity.INFO,
                    component.getName() + " SNMP router metrics are normal",
                    rawLog
            );
        } catch (SocketTimeoutException exception) {
            return snmpRouterMetricsFailure(component, port, "SNMP request timed out");
        } catch (IOException | IllegalArgumentException exception) {
            return snmpRouterMetricsFailure(component, port, exception.getMessage());
        }
    }

    private CheckResult snmpRouterMetricsFailure(MonitoredComponent component, int port, String reason) {
        return new CheckResult(
                MonitoringMethod.SNMP_ROUTER_METRICS,
                ComponentStatus.DOWN,
                "SNMP_ROUTER_METRICS_FAILED",
                severityFor(component),
                component.getName() + " SNMP router metrics could not be collected",
                "snmp router metrics " + component.getIpAddress() + ":" + port + " failed: " + reason
        );
    }

    private CheckResult checkSnmpServerMetrics(MonitoredComponent component) {
        int port = component.getSnmpPort() == null ? 161 : component.getSnmpPort();
        String community = component.getSnmpCommunity();

        try {
            SnmpDecodedValue uptime = snmpGet(component, port, community, "1.3.6.1.2.1.1.3.0");
            OptionalLong cpuIdle = readSnmpNumberSafely(component, port, community, "1.3.6.1.4.1.2021.11.11.0");
            OptionalLong cpuUser = readSnmpNumberSafely(component, port, community, "1.3.6.1.4.1.2021.11.9.0");
            OptionalLong cpuSystem = readSnmpNumberSafely(component, port, community, "1.3.6.1.4.1.2021.11.10.0");
            OptionalLong memoryTotalKb = readSnmpNumberSafely(component, port, community, "1.3.6.1.4.1.2021.4.5.0");
            OptionalLong memoryAvailableKb = readSnmpNumberSafely(component, port, community, "1.3.6.1.4.1.2021.4.6.0");
            OptionalLong processes = readSnmpNumberSafely(component, port, community, "1.3.6.1.2.1.25.1.6.0");
            OptionalLong users = readSnmpNumberSafely(component, port, community, "1.3.6.1.2.1.25.1.5.0");
            java.util.Optional<String> loadAverage = readSnmpTextSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.4.1.2021.10.1.3.1"
            );
            List<StorageMetric> storageMetrics = readStorageMetrics(component, port, community);
            List<String> storageSummaries = storageMetrics.stream()
                    .map(metric -> metric.description()
                            + " used=" + metric.usedPercent() + "%"
                            + " sizeMb=" + metric.sizeMb())
                    .toList();

            List<String> issues = new ArrayList<>();
            long cpuUsage = cpuIdle.isPresent() ? 100 - cpuIdle.getAsLong() : -1;
            if (cpuUsage >= 85) {
                issues.add("CPU usage is high: " + cpuUsage + "%");
            }

            long memoryUsedPercent = -1;
            if (memoryTotalKb.isPresent() && memoryAvailableKb.isPresent() && memoryTotalKb.getAsLong() > 0) {
                long usedKb = memoryTotalKb.getAsLong() - memoryAvailableKb.getAsLong();
                memoryUsedPercent = Math.round((usedKb * 100.0) / memoryTotalKb.getAsLong());
                if (memoryUsedPercent >= 90) {
                    issues.add("Memory usage is high: " + memoryUsedPercent + "%");
                }
            }

            storageMetrics.stream()
                    .filter(metric -> metric.usedPercent() >= 90)
                    .forEach(metric -> issues.add(metric.description() + " disk usage is high: " + metric.usedPercent() + "%"));

            String rawLog = "SNMP server metrics: uptime=" + uptime.value()
                    + ", cpuUsagePercent=" + valueOrUnavailable(cpuUsage)
                    + ", cpuUserPercent=" + optionalOrUnavailable(cpuUser)
                    + ", cpuSystemPercent=" + optionalOrUnavailable(cpuSystem)
                    + ", memoryUsedPercent=" + valueOrUnavailable(memoryUsedPercent)
                    + ", memoryTotalKb=" + optionalOrUnavailable(memoryTotalKb)
                    + ", memoryAvailableKb=" + optionalOrUnavailable(memoryAvailableKb)
                    + ", loadAverage1min=" + loadAverage.orElse("unavailable")
                    + ", users=" + optionalOrUnavailable(users)
                    + ", processes=" + optionalOrUnavailable(processes)
                    + ", storage=[" + String.join("; ", storageSummaries) + "]";

            if (!issues.isEmpty()) {
                return new CheckResult(
                        MonitoringMethod.SNMP_SERVER_METRICS,
                        ComponentStatus.DEGRADED,
                        "SNMP_SERVER_METRICS_DEGRADED",
                        severityFor(component),
                        component.getName() + " SNMP metrics show degraded workstation/server health",
                        rawLog + ", issues=[" + String.join("; ", issues) + "]"
                );
            }

            return new CheckResult(
                    MonitoringMethod.SNMP_SERVER_METRICS,
                    ComponentStatus.UP,
                    "SNMP_SERVER_METRICS_NORMAL",
                    Severity.INFO,
                    component.getName() + " SNMP workstation/server metrics are normal",
                    rawLog
            );
        } catch (SocketTimeoutException exception) {
            return snmpServerMetricsFailure(component, port, "SNMP request timed out");
        } catch (IOException | IllegalArgumentException exception) {
            return snmpServerMetricsFailure(component, port, exception.getMessage());
        }
    }

    private CheckResult snmpServerMetricsFailure(MonitoredComponent component, int port, String reason) {
        return new CheckResult(
                MonitoringMethod.SNMP_SERVER_METRICS,
                ComponentStatus.DOWN,
                "SNMP_SERVER_METRICS_FAILED",
                severityFor(component),
                component.getName() + " SNMP workstation/server metrics could not be collected",
                "snmp server metrics " + component.getIpAddress() + ":" + port + " failed: " + reason
        );
    }

    private CheckResult checkUpsSnmpMetrics(MonitoredComponent component) {
        int port = component.getSnmpPort() == null ? 161 : component.getSnmpPort();
        String community = component.getSnmpCommunity();

        try {
            SnmpDecodedValue sysUptime = snmpGet(component, port, community, "1.3.6.1.2.1.1.3.0");
            java.util.Optional<String> manufacturer = readSnmpTextSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.33.1.1.1.0"
            );
            java.util.Optional<String> model = readSnmpTextSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.33.1.1.2.0"
            );
            OptionalLong batteryStatusValue = readSnmpNumberSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.33.1.2.1.0"
            );
            OptionalLong runtimeMinutes = readSnmpNumberSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.33.1.2.3.0"
            );
            OptionalLong batteryPercent = readSnmpNumberSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.33.1.2.4.0"
            );
            OptionalLong outputSourceValue = readSnmpNumberSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.33.1.4.1.0"
            );
            OptionalLong inputVoltage = readSnmpNumberSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.33.1.3.3.1.3.1"
            );
            OptionalLong outputVoltage = readSnmpNumberSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.33.1.4.4.1.2.1"
            );
            OptionalLong loadPercent = readSnmpNumberSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.33.1.4.4.1.5.1"
            );

            boolean hasUpsMetric = batteryStatusValue.isPresent()
                    || runtimeMinutes.isPresent()
                    || batteryPercent.isPresent()
                    || outputSourceValue.isPresent()
                    || inputVoltage.isPresent()
                    || outputVoltage.isPresent()
                    || loadPercent.isPresent();
            if (!hasUpsMetric) {
                return new CheckResult(
                        MonitoringMethod.UPS_SNMP_METRICS,
                        ComponentStatus.DOWN,
                        "UPS_SNMP_METRICS_UNAVAILABLE",
                        severityFor(component),
                        component.getName() + " answers SNMP but does not expose standard UPS-MIB metrics",
                        "SNMP UPS-MIB metrics unavailable on " + component.getIpAddress()
                                + ":" + port + ", sysUpTime=" + sysUptime.value()
                );
            }

            String batteryStatus = batteryStatusValue.isPresent()
                    ? upsBatteryStatusName(batteryStatusValue.getAsLong())
                    : "unavailable";
            String outputSource = outputSourceValue.isPresent()
                    ? upsOutputSourceName(outputSourceValue.getAsLong())
                    : "unavailable";
            List<String> issues = new ArrayList<>();
            String eventType = "UPS_SNMP_METRICS_NORMAL";
            Severity severity = Severity.INFO;
            ComponentStatus resultStatus = ComponentStatus.UP;

            if ("battery".equals(outputSource)) {
                issues.add("UPS output source is battery");
                eventType = "UPS_ON_BATTERY";
                severity = Severity.WARNING;
                resultStatus = ComponentStatus.DEGRADED;
            }
            if ("low".equals(batteryStatus) || "depleted".equals(batteryStatus)) {
                issues.add("UPS battery status is " + batteryStatus);
                eventType = "UPS_BATTERY_LOW";
                severity = Severity.CRITICAL;
                resultStatus = ComponentStatus.DEGRADED;
            }
            if (batteryPercent.isPresent() && batteryPercent.getAsLong() <= 15) {
                issues.add("UPS battery is low: " + batteryPercent.getAsLong() + "%");
                eventType = "UPS_BATTERY_LOW";
                severity = Severity.CRITICAL;
                resultStatus = ComponentStatus.DEGRADED;
            }
            if (runtimeMinutes.isPresent() && runtimeMinutes.getAsLong() <= 10) {
                issues.add("UPS runtime is low: " + runtimeMinutes.getAsLong() + " minutes");
                eventType = "UPS_RUNTIME_LOW";
                severity = Severity.CRITICAL;
                resultStatus = ComponentStatus.DEGRADED;
            }
            if (loadPercent.isPresent() && loadPercent.getAsLong() >= 85) {
                issues.add("UPS load is high: " + loadPercent.getAsLong() + "%");
                eventType = "UPS_LOAD_HIGH";
                severity = severity == Severity.CRITICAL ? Severity.CRITICAL : Severity.WARNING;
                resultStatus = ComponentStatus.DEGRADED;
            }

            String rawLog = "UPS SNMP metrics: manufacturer=" + manufacturer.orElse("unavailable")
                    + ", model=" + model.orElse("unavailable")
                    + ", sysUptime=" + sysUptime.value()
                    + ", batteryStatus=" + batteryStatus
                    + ", batteryPercent=" + optionalOrUnavailable(batteryPercent)
                    + ", runtimeMinutes=" + optionalOrUnavailable(runtimeMinutes)
                    + ", outputSource=" + outputSource
                    + ", inputVoltage=" + optionalOrUnavailable(inputVoltage)
                    + ", outputVoltage=" + optionalOrUnavailable(outputVoltage)
                    + ", loadPercent=" + optionalOrUnavailable(loadPercent);

            if (!issues.isEmpty()) {
                return new CheckResult(
                        MonitoringMethod.UPS_SNMP_METRICS,
                        resultStatus,
                        eventType,
                        severity,
                        component.getName() + " UPS SNMP metrics show power protection risk",
                        rawLog + ", issues=[" + String.join("; ", issues) + "]"
                );
            }

            return new CheckResult(
                    MonitoringMethod.UPS_SNMP_METRICS,
                    ComponentStatus.UP,
                    eventType,
                    Severity.INFO,
                    component.getName() + " UPS SNMP metrics are normal",
                    rawLog
            );
        } catch (SocketTimeoutException exception) {
            return upsSnmpMetricsFailure(component, port, "SNMP request timed out");
        } catch (IOException | IllegalArgumentException exception) {
            return upsSnmpMetricsFailure(component, port, exception.getMessage());
        }
    }

    private CheckResult upsSnmpMetricsFailure(MonitoredComponent component, int port, String reason) {
        return new CheckResult(
                MonitoringMethod.UPS_SNMP_METRICS,
                ComponentStatus.DOWN,
                "UPS_SNMP_METRICS_FAILED",
                severityFor(component),
                component.getName() + " UPS SNMP metrics could not be collected",
                "snmp ups metrics " + component.getIpAddress() + ":" + port + " failed: " + reason
        );
    }

    private String upsBatteryStatusName(long value) {
        return switch ((int) value) {
            case 1 -> "unknown";
            case 2 -> "normal";
            case 3 -> "low";
            case 4 -> "depleted";
            default -> "status-" + value;
        };
    }

    private String upsOutputSourceName(long value) {
        return switch ((int) value) {
            case 1 -> "other";
            case 2 -> "none";
            case 3 -> "normal";
            case 4 -> "bypass";
            case 5 -> "battery";
            case 6 -> "booster";
            case 7 -> "reducer";
            default -> "source-" + value;
        };
    }

    private List<StorageMetric> readStorageMetrics(MonitoredComponent component, int port, String community) {
        List<StorageMetric> metrics = new ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            java.util.Optional<String> description = readSnmpTextSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.25.2.3.1.3." + index
            );
            OptionalLong allocationUnits = readSnmpNumberSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.25.2.3.1.4." + index
            );
            OptionalLong size = readSnmpNumberSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.25.2.3.1.5." + index
            );
            OptionalLong used = readSnmpNumberSafely(
                    component,
                    port,
                    community,
                    "1.3.6.1.2.1.25.2.3.1.6." + index
            );

            if (description.isEmpty() || allocationUnits.isEmpty() || size.isEmpty() || used.isEmpty() || size.getAsLong() <= 0) {
                continue;
            }

            String name = description.get();
            if (!isRelevantStorage(name)) {
                continue;
            }

            long usedPercent = Math.round((used.getAsLong() * 100.0) / size.getAsLong());
            long sizeMb = Math.round((size.getAsLong() * allocationUnits.getAsLong()) / (1024.0 * 1024.0));
            metrics.add(new StorageMetric(name, usedPercent, sizeMb));
        }
        return metrics;
    }

    private boolean isRelevantStorage(String description) {
        String value = description.toLowerCase();
        return value.equals("/")
                || value.startsWith("/")
                || value.contains("physical memory")
                || value.contains("real memory")
                || value.contains("swap");
    }

    private SnmpDecodedValue snmpGet(MonitoredComponent component, int port, String community, String oid) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout((int) HTTP_TIMEOUT.toMillis());
            byte[] request = SnmpV2cGetEncoder.encodeGet(community, oid);
            DatagramPacket packet = new DatagramPacket(
                    request,
                    request.length,
                    InetAddress.getByName(component.getIpAddress()),
                    port
            );
            socket.send(packet);

            byte[] buffer = new byte[1500];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            return decodeSnmpResponse(response.getData(), response.getLength());
        }
    }

    private OptionalLong readSnmpNumber(MonitoredComponent component, int port, String community, String oid) throws IOException {
        SnmpDecodedValue value = snmpGet(component, port, community, oid);
        return parseSnmpNumber(value.value());
    }

    private OptionalLong readSnmpNumberSafely(MonitoredComponent component, int port, String community, String oid) {
        try {
            return readSnmpNumber(component, port, community, oid);
        } catch (IOException | IllegalArgumentException exception) {
            return OptionalLong.empty();
        }
    }

    private java.util.Optional<String> readSnmpText(MonitoredComponent component, int port, String community, String oid) throws IOException {
        SnmpDecodedValue value = snmpGet(component, port, community, oid);
        String text = value.value();
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            text = text.substring(1, text.length() - 1);
        }
        if (text.startsWith("noSuch") || text.equals("NULL")) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(text);
    }

    private java.util.Optional<String> readSnmpTextSafely(MonitoredComponent component, int port, String community, String oid) {
        try {
            return readSnmpText(component, port, community, oid);
        } catch (IOException | IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private OptionalLong parseSnmpNumber(String value) {
        String candidate = value
                .replace("Counter32 ", "")
                .replace("Gauge32 ", "")
                .replace("Timeticks ", "")
                .replace("Counter64 ", "")
                .trim();
        if (candidate.startsWith("noSuch") || candidate.equals("NULL") || candidate.startsWith("\"")) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(candidate));
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    private String interfaceStatusName(long value) {
        return switch ((int) value) {
            case 1 -> "up";
            case 2 -> "down";
            case 3 -> "testing";
            case 4 -> "unknown";
            case 5 -> "dormant";
            case 6 -> "notPresent";
            case 7 -> "lowerLayerDown";
            default -> "status-" + value;
        };
    }

    private String optionalOrUnavailable(OptionalLong value) {
        return value.isPresent() ? String.valueOf(value.getAsLong()) : "unavailable";
    }

    private String valueOrUnavailable(long value) {
        return value >= 0 ? String.valueOf(value) : "unavailable";
    }

    private CheckResult snmpFailure(MonitoredComponent component, int port, String oid, String reason) {
        return new CheckResult(
                MonitoringMethod.SNMP_BASIC,
                ComponentStatus.DOWN,
                "SNMP_CHECK_FAILED",
                severityFor(component),
                component.getName() + " did not respond to SNMP basic check",
                "snmpget " + component.getIpAddress() + ":" + port + " " + oid + " failed: " + reason
        );
    }

    private SnmpDecodedValue decodeSnmpResponse(byte[] data, int length) throws IOException {
        BerReader root = new BerReader(Arrays.copyOf(data, length));
        BerValue message = root.read();
        BerReader messageReader = new BerReader(message.value());
        messageReader.read(); // version
        messageReader.read(); // community
        BerValue pdu = messageReader.read();
        BerReader pduReader = new BerReader(pdu.value());
        pduReader.read(); // request id
        BerValue errorStatus = pduReader.read();
        pduReader.read(); // error index
        int errorCode = decodeInteger(errorStatus.value());
        if (errorCode != 0) {
            throw new IOException("SNMP error status " + errorCode);
        }

        BerValue varbindList = pduReader.read();
        BerReader varbindListReader = new BerReader(varbindList.value());
        BerValue varbind = varbindListReader.read();
        BerReader varbindReader = new BerReader(varbind.value());
        BerValue oid = varbindReader.read();
        BerValue value = varbindReader.read();
        return new SnmpDecodedValue(decodeOid(oid.value()), decodeSnmpValue(value));
    }

    private String decodeSnmpValue(BerValue value) {
        int tag = value.tag() & 0xFF;
        byte[] bytes = value.value();
        return switch (tag) {
            case 0x02 -> String.valueOf(decodeInteger(bytes));
            case 0x04 -> decodeOctetString(bytes);
            case 0x05 -> "NULL";
            case 0x06 -> decodeOid(bytes);
            case 0x40 -> decodeIpAddress(bytes);
            case 0x41 -> "Counter32 " + decodeUnsigned(bytes);
            case 0x42 -> "Gauge32 " + decodeUnsigned(bytes);
            case 0x43 -> "Timeticks " + decodeUnsigned(bytes);
            case 0x46 -> "Counter64 " + decodeUnsigned(bytes);
            case 0x80 -> "noSuchObject";
            case 0x81 -> "noSuchInstance";
            case 0x82 -> "endOfMibView";
            default -> "BER tag 0x" + Integer.toHexString(tag) + " length " + bytes.length;
        };
    }

    private String decodeOctetString(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        boolean printable = text.chars().allMatch(character -> character == '\n'
                || character == '\r'
                || character == '\t'
                || (character >= 32 && character < 127));
        if (printable && !text.isBlank()) {
            return "\"" + text + "\"";
        }
        StringBuilder hex = new StringBuilder("0x");
        for (byte item : bytes) {
            hex.append(String.format("%02x", item));
        }
        return hex.toString();
    }

    private String decodeIpAddress(byte[] bytes) {
        if (bytes.length != 4) {
            return "invalid-ip";
        }
        return (bytes[0] & 0xFF) + "."
                + (bytes[1] & 0xFF) + "."
                + (bytes[2] & 0xFF) + "."
                + (bytes[3] & 0xFF);
    }

    private int decodeInteger(byte[] bytes) {
        int value = 0;
        for (byte item : bytes) {
            value = (value << 8) | (item & 0xFF);
        }
        if (bytes.length > 0 && (bytes[0] & 0x80) != 0) {
            value -= 1 << (bytes.length * 8);
        }
        return value;
    }

    private long decodeUnsigned(byte[] bytes) {
        long value = 0;
        for (byte item : bytes) {
            value = (value << 8) | (item & 0xFFL);
        }
        return value;
    }

    private String decodeOid(byte[] bytes) {
        if (bytes.length == 0) {
            return "";
        }
        List<Integer> parts = new ArrayList<>();
        int first = bytes[0] & 0xFF;
        parts.add(first / 40);
        parts.add(first % 40);
        int value = 0;
        for (int i = 1; i < bytes.length; i++) {
            int current = bytes[i] & 0xFF;
            value = (value << 7) | (current & 0x7F);
            if ((current & 0x80) == 0) {
                parts.add(value);
                value = 0;
            }
        }
        return String.join(".", parts.stream().map(String::valueOf).toList());
    }

    private Severity severityFor(MonitoredComponent component) {
        return component.getCriticality() == Criticality.HIGH || component.getCriticality() == Criticality.CRITICAL
                ? Severity.CRITICAL
                : Severity.WARNING;
    }

    private String componentDeviceId(MonitoredComponent component) {
        return "component-" + component.getId();
    }

    private record CheckResult(
            MonitoringMethod method,
            ComponentStatus status,
            String eventType,
            Severity severity,
            String message,
            String rawLog
    ) {
    }

    private record SnmpDecodedValue(String oid, String value) {
    }

    private record StorageMetric(String description, long usedPercent, long sizeMb) {
    }

    private record BerValue(byte tag, byte[] value) {
    }

    private static class BerReader {

        private final byte[] data;
        private int offset;

        BerReader(byte[] data) {
            this.data = data;
        }

        BerValue read() throws IOException {
            if (offset >= data.length) {
                throw new IOException("Unexpected end of BER data");
            }
            byte tag = data[offset++];
            int length = readLength();
            if (offset + length > data.length) {
                throw new IOException("Invalid BER length");
            }
            byte[] value = Arrays.copyOfRange(data, offset, offset + length);
            offset += length;
            return new BerValue(tag, value);
        }

        private int readLength() throws IOException {
            if (offset >= data.length) {
                throw new IOException("Missing BER length");
            }
            int first = data[offset++] & 0xFF;
            if ((first & 0x80) == 0) {
                return first;
            }
            int byteCount = first & 0x7F;
            if (byteCount == 0 || byteCount > 4 || offset + byteCount > data.length) {
                throw new IOException("Unsupported BER length");
            }
            int length = 0;
            for (int i = 0; i < byteCount; i++) {
                length = (length << 8) | (data[offset++] & 0xFF);
            }
            return length;
        }
    }
}
