package com.aiops.backend.pcap;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping
public class PacketCaptureAnalysisController {

    private final PacketCaptureAnalysisService service;

    public PacketCaptureAnalysisController(PacketCaptureAnalysisService service) {
        this.service = service;
    }

    @PostMapping("/api/incidents/{incidentId:\\d+}/pcap")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public PacketCaptureAnalysisResponse upload(
            @PathVariable Long incidentId,
            @RequestParam("file") MultipartFile file
    ) {
        return service.upload(incidentId, file);
    }

    @GetMapping("/api/incidents/{incidentId:\\d+}/pcap")
    public List<PacketCaptureAnalysisResponse> listForIncident(@PathVariable Long incidentId) {
        return service.listForIncident(incidentId);
    }

    @DeleteMapping("/api/incidents/{incidentId:\\d+}/pcap/{analysisId:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public void delete(@PathVariable Long incidentId, @PathVariable Long analysisId) {
        service.delete(incidentId, analysisId);
    }

    @GetMapping("/api/packet-captures/{id:\\d+}")
    public PacketCaptureAnalysisResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
