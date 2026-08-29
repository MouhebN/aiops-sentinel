package com.aiops.backend.pcap;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
public class PacketCaptureJobController {

    private final PacketCaptureJobService service;

    public PacketCaptureJobController(PacketCaptureJobService service) {
        this.service = service;
    }

    @GetMapping("/api/packet-captures/provider-health")
    public PacketCaptureProviderHealth providerHealth() {
        return service.providerHealth();
    }

    @GetMapping("/api/incidents/{incidentId:\\d+}/packet-captures/preview")
    public PacketCapturePreviewResponse preview(@PathVariable Long incidentId) {
        return service.preview(incidentId);
    }

    @PostMapping("/api/incidents/{incidentId:\\d+}/packet-captures/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public PacketCaptureJobResponse start(
            @PathVariable Long incidentId,
            @RequestBody(required = false) StartPacketCaptureRequest request
    ) {
        return service.start(incidentId, request);
    }

    @GetMapping("/api/incidents/{incidentId:\\d+}/packet-captures/jobs")
    public List<PacketCaptureJobResponse> list(@PathVariable Long incidentId) {
        return service.list(incidentId);
    }

    @GetMapping("/api/incidents/{incidentId:\\d+}/packet-captures/jobs/{jobId:\\d+}")
    public PacketCaptureJobResponse get(@PathVariable Long incidentId, @PathVariable Long jobId) {
        return service.get(incidentId, jobId);
    }

    @PostMapping("/api/incidents/{incidentId:\\d+}/packet-captures/jobs/{jobId:\\d+}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public PacketCaptureJobResponse cancel(@PathVariable Long incidentId, @PathVariable Long jobId) {
        return service.cancel(incidentId, jobId);
    }
}
