package com.aiops.backend.pcap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PacketCaptureRouteMappingTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PacketCaptureJobService jobService;

    @MockitoBean
    private PacketCaptureAnalysisService analysisService;

    @Test
    void providerHealthIsNotParsedAsNumericId() throws Exception {
        when(jobService.providerHealth()).thenReturn(
                new PacketCaptureProviderHealth(true, "UP", "Capture sensor is reachable")
        );

        mockMvc.perform(get("/api/packet-captures/provider-health")
                        .header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.status").value("UP"));

        verify(jobService).providerHealth();
        verify(analysisService, never()).get(any());
    }

    @Test
    void numericPacketCaptureIdReachesAnalysisHandler() throws Exception {
        when(analysisService.get(123L)).thenReturn(analysis(123L));

        mockMvc.perform(get("/api/packet-captures/123")
                        .header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.fileSize").value(4200))
                .andExpect(jsonPath("$.totalBytes").value(4200))
                .andExpect(jsonPath("$.capturedPacketBytes").value(4200));

        verify(analysisService).get(123L);
    }

    @Test
    void nonNumericPacketCapturePathReturnsControlledNotFound() throws Exception {
        mockMvc.perform(get("/api/packet-captures/abc")
                        .header("Authorization", "Bearer " + login()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(analysisService, never()).get(any());
        verify(jobService, never()).providerHealth();
    }

    @Test
    void previewReachesPreviewHandler() throws Exception {
        when(jobService.preview(34L)).thenReturn(new PacketCapturePreviewResponse(
                "10.0.0.10",
                "10.10.10.20",
                "BANK-SRV-01",
                "Service LAN",
                "10.10.10.20",
                "bank-firewall-wan",
                "BANK-FW-01 / WAN",
                "eth1",
                20,
                true,
                null
        ));

        mockMvc.perform(get("/api/incidents/34/packet-captures/preview")
                        .header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceIp").value("10.0.0.10"))
                .andExpect(jsonPath("$.capturePoint").value("BANK-FW-01 / WAN"));

        verify(jobService).preview(34L);
    }

    @Test
    void startReachesStartHandler() throws Exception {
        when(jobService.start(eq(34L), any())).thenReturn(job(10L, 34L, PacketCaptureJobStatus.RUNNING));

        mockMvc.perform(post("/api/incidents/34/packet-captures/start")
                        .header("Authorization", "Bearer " + login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        verify(jobService).start(eq(34L), any());
    }

    @Test
    void jobsListReachesJobsHandler() throws Exception {
        when(jobService.list(34L)).thenReturn(List.of(job(10L, 34L, PacketCaptureJobStatus.COMPLETED)));

        mockMvc.perform(get("/api/incidents/34/packet-captures/jobs")
                        .header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));

        verify(jobService).list(34L);
        verify(jobService, never()).get(any(), any());
    }

    @Test
    void jobByIdReachesJobHandler() throws Exception {
        when(jobService.get(34L, 10L)).thenReturn(job(10L, 34L, PacketCaptureJobStatus.COMPLETED));

        mockMvc.perform(get("/api/incidents/34/packet-captures/jobs/10")
                        .header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.incidentId").value(34));

        verify(jobService).get(34L, 10L);
    }

    @Test
    void cancelReachesCancelHandler() throws Exception {
        when(jobService.cancel(34L, 10L)).thenReturn(job(10L, 34L, PacketCaptureJobStatus.CANCELLED));

        mockMvc.perform(post("/api/incidents/34/packet-captures/jobs/10/cancel")
                        .header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(jobService).cancel(34L, 10L);
    }

    private PacketCaptureJobResponse job(Long id, Long incidentId, PacketCaptureJobStatus status) {
        Instant now = Instant.parse("2026-08-25T15:00:00Z");
        return new PacketCaptureJobResponse(
                id,
                incidentId,
                "LAB_SENSOR",
                status,
                "10.0.0.10",
                "10.10.10.20",
                "BANK-SRV-01",
                "10.10.10.20",
                "bank-firewall-wan",
                "BANK-FW-01 / WAN",
                "eth1",
                20,
                now,
                now,
                23,
                4200L,
                status == PacketCaptureJobStatus.CANCELLED ? PacketCaptureFailureCode.CANCELLED : null,
                null,
                99L,
                now,
                CaptureTrigger.MANUAL,
                0,
                20,
                now,
                now,
                now.plusSeconds(20)
        );
    }

    private PacketCaptureAnalysisResponse analysis(Long id) {
        return new PacketCaptureAnalysisResponse(
                id,
                34L,
                "capture.pcap",
                "application/octet-stream",
                4200,
                23,
                4200,
                List.of("10.0.0.10 (23)"),
                List.of("10.10.10.20 (23)"),
                List.of("TCP (23)"),
                List.of("22 (4)"),
                List.of(),
                "Analyzed 23 packets.",
                Instant.parse("2026-08-25T15:00:00Z"),
                4200
        );
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"operator@aiops.local","password":"operator123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String marker = "\"token\":\"";
        int start = body.indexOf(marker) + marker.length();
        return body.substring(start, body.indexOf('"', start));
    }
}
