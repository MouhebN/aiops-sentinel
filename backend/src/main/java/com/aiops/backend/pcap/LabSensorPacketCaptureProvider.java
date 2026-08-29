package com.aiops.backend.pcap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@Primary
public class LabSensorPacketCaptureProvider implements PacketCaptureProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LabSensorPacketCaptureProvider.class);

    private final PcapCaptureProperties properties;
    private final RestTemplate restTemplate;
    private final RestTemplate fileRestTemplate;

    public LabSensorPacketCaptureProvider(PcapCaptureProperties properties) {
        this.properties = properties;
        this.restTemplate = client(
                Duration.ofMillis(properties.getLabSensor().getConnectTimeoutMs()),
                Duration.ofMillis(properties.getLabSensor().getReadTimeoutMs())
        );
        this.fileRestTemplate = client(
                Duration.ofMillis(properties.getLabSensor().getConnectTimeoutMs()),
                Duration.ofMillis(properties.getLabSensor().getFileReadTimeoutMs())
        );
    }

    @Override
    public String providerId() {
        return "LAB_SENSOR";
    }

    @Override
    public PacketCaptureProviderHealth health() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.getForObject(baseUrl() + "/health", Map.class);
            boolean tcpdump = body != null && Boolean.TRUE.equals(body.get("tcpdump"));
            String status = body == null ? "UNKNOWN" : String.valueOf(body.getOrDefault("status", "UNKNOWN"));
            Boolean rollingEnabled = null;
            Boolean rollingRunning = null;
            if (body != null && body.get("rolling") instanceof Map<?, ?> rolling) {
                Object enabled = rolling.get("enabled");
                Object running = rolling.get("running");
                if (enabled instanceof Boolean flag) {
                    rollingEnabled = flag;
                }
                if (running instanceof Boolean flag) {
                    rollingRunning = flag;
                }
            }
            if (!tcpdump) {
                return new PacketCaptureProviderHealth(
                        false,
                        status,
                        "Capture sensor is up but tcpdump is unavailable",
                        rollingEnabled,
                        rollingRunning
                );
            }
            String message = "Capture sensor is reachable";
            if (Boolean.TRUE.equals(rollingEnabled) && Boolean.FALSE.equals(rollingRunning)) {
                message = "Capture sensor is reachable but the rolling buffer is not running";
            }
            return new PacketCaptureProviderHealth(true, status, message, rollingEnabled, rollingRunning);
        } catch (RestClientException exception) {
            LOGGER.info("Lab capture sensor unavailable: {}", exception.getMessage());
            return new PacketCaptureProviderHealth(
                    false,
                    "DOWN",
                    "Capture sensor is unavailable: " + exception.getMessage()
            );
        }
    }

    @Override
    public ProviderCaptureHandle startCapture(ProviderCaptureRequest request) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("interface", request.interfaceName());
            body.put("sourceIp", request.sourceIp() == null ? "" : request.sourceIp());
            body.put("destinationIp", request.destinationIp() == null ? "" : request.destinationIp());
            String path = "/captures";
            if (request.rollingSnapshot()) {
                path = "/snapshots";
                body.put("preTriggerSeconds", request.preTriggerSeconds());
                body.put("postTriggerSeconds", request.postTriggerSeconds() == null
                        ? request.durationSeconds()
                        : request.postTriggerSeconds());
            } else {
                body.put("durationSeconds", request.durationSeconds());
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            LabSensorCaptureResponse response = restTemplate.postForObject(
                    baseUrl() + path,
                    new HttpEntity<>(body, headers),
                    LabSensorCaptureResponse.class
            );
            if (response == null || response.id() == null) {
                throw new CaptureProviderException(
                        PacketCaptureFailureCode.PROVIDER_UNAVAILABLE,
                        "Capture sensor returned an empty start response"
                );
            }
            return new ProviderCaptureHandle(response.id(), mapStatus(response.status()));
        } catch (ResourceAccessException exception) {
            throw new CaptureProviderException(
                    PacketCaptureFailureCode.PROVIDER_UNAVAILABLE,
                    "Capture sensor is unavailable",
                    exception
            );
        } catch (HttpStatusCodeException exception) {
            throw mapHttp(exception);
        }
    }

    @Override
    public ProviderCaptureStatus getCaptureStatus(String providerCaptureId) {
        try {
            LabSensorCaptureResponse response = restTemplate.getForObject(
                    baseUrl() + "/captures/" + providerCaptureId,
                    LabSensorCaptureResponse.class
            );
            if (response == null) {
                throw new CaptureProviderException(
                        PacketCaptureFailureCode.TRANSFER_FAILURE,
                        "Capture sensor returned an empty status"
                );
            }
            return new ProviderCaptureStatus(
                    response.id(),
                    mapStatus(response.status()),
                    response.packetCount(),
                    response.fileSizeBytes(),
                    mapFailure(response.errorCode()),
                    response.errorMessage()
            );
        } catch (ResourceAccessException exception) {
            throw new CaptureProviderException(
                    PacketCaptureFailureCode.PROVIDER_UNAVAILABLE,
                    "Capture sensor is unavailable",
                    exception
            );
        } catch (HttpStatusCodeException exception) {
            throw mapHttp(exception);
        }
    }

    @Override
    public byte[] retrieveCapture(String providerCaptureId) {
        try {
            ResponseEntity<byte[]> response = fileRestTemplate.getForEntity(
                    baseUrl() + "/captures/" + providerCaptureId + "/file",
                    byte[].class
            );
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new CaptureProviderException(
                        PacketCaptureFailureCode.EMPTY_CAPTURE,
                        "Capture sensor returned an empty PCAP"
                );
            }
            long max = properties.getCapture().getMaxFileSizeBytes();
            if (body.length > max) {
                throw new CaptureProviderException(
                        PacketCaptureFailureCode.TRANSFER_FAILURE,
                        "Capture file exceeds the " + max + " byte limit"
                );
            }
            return body;
        } catch (ResourceAccessException exception) {
            throw new CaptureProviderException(
                    PacketCaptureFailureCode.TRANSFER_FAILURE,
                    "Could not download capture file from the sensor",
                    exception
            );
        } catch (HttpStatusCodeException exception) {
            throw mapHttp(exception);
        }
    }

    @Override
    public void cancelCapture(String providerCaptureId) {
        try {
            restTemplate.delete(baseUrl() + "/captures/" + providerCaptureId);
        } catch (RestClientException exception) {
            LOGGER.info("Could not cancel lab capture {}: {}", providerCaptureId, exception.getMessage());
        }
    }

    private RestTemplate client(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connect.toMillis());
        factory.setReadTimeout((int) read.toMillis());
        return new RestTemplate(factory);
    }

    private String baseUrl() {
        String url = properties.getLabSensor().getBaseUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private PacketCaptureJobStatus mapStatus(String status) {
        if (status == null) {
            return PacketCaptureJobStatus.RUNNING;
        }
        try {
            return PacketCaptureJobStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return PacketCaptureJobStatus.RUNNING;
        }
    }

    private PacketCaptureFailureCode mapFailure(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return PacketCaptureFailureCode.valueOf(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return PacketCaptureFailureCode.TCPDUMP_FAILURE;
        }
    }

    private CaptureProviderException mapHttp(HttpStatusCodeException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        String body = exception.getResponseBodyAsString();
        if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.BAD_GATEWAY) {
            return new CaptureProviderException(PacketCaptureFailureCode.PROVIDER_UNAVAILABLE, "Capture sensor is unavailable");
        }
        if (status == HttpStatus.CONFLICT) {
            return new CaptureProviderException(PacketCaptureFailureCode.BUSY, "A capture is already running on the sensor");
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return new CaptureProviderException(
                    PacketCaptureFailureCode.INVALID_REQUEST,
                    body == null || body.isBlank() ? "Capture sensor rejected the request" : body
            );
        }
        return new CaptureProviderException(
                PacketCaptureFailureCode.TCPDUMP_FAILURE,
                body == null || body.isBlank() ? exception.getMessage() : body
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LabSensorCaptureResponse(
            String id,
            String status,
            @JsonProperty("interface") String interfaceName,
            String sourceIp,
            String destinationIp,
            Integer durationSeconds,
            String filterExpression,
            Integer packetCount,
            Long fileSizeBytes,
            String errorCode,
            String errorMessage
    ) {
    }
}
