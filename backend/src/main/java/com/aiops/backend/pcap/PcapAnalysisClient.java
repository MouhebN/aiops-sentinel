package com.aiops.backend.pcap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PcapAnalysisClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PcapAnalysisClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String aiServiceBaseUrl;

    public PcapAnalysisClient(
            @Value("${app.ai-service.base-url:http://localhost:8001}") String aiServiceBaseUrl
    ) {
        this.aiServiceBaseUrl = aiServiceBaseUrl;
    }

    public FastApiPacketCaptureAnalysisResponse analyze(String fileName, String contentType, byte[] bytes) {
        String safeName = fileName == null || fileName.isBlank() ? "capture.pcap" : fileName;
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return safeName;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(
                contentType == null || contentType.isBlank()
                        ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                        : contentType
        ));
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, fileHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, requestHeaders);

        LOGGER.info(
                "Forwarding packet capture to FastAPI: filename={}, size={}, target={}",
                safeName,
                bytes.length,
                aiServiceBaseUrl + "/api/analyze-pcap"
        );

        try {
            FastApiPacketCaptureAnalysisResponse response = restTemplate.postForObject(
                    aiServiceBaseUrl + "/api/analyze-pcap",
                    requestEntity,
                    FastApiPacketCaptureAnalysisResponse.class
            );
            if (response == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI service returned an empty packet capture analysis"
                );
            }
            return response;
        } catch (HttpStatusCodeException exception) {
            String detail = exception.getResponseBodyAsString();
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Packet capture analysis failed: "
                            + (detail == null || detail.isBlank() ? exception.getMessage() : detail)
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Packet capture analysis failed: " + exception.getMessage()
            );
        }
    }
}
