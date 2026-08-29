package com.aiops.backend.incident;

import com.aiops.backend.ai.AiContextBuilderService;
import com.aiops.backend.ai.AiIncidentContextResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final AiContextBuilderService aiContextBuilderService;

    public IncidentController(IncidentService incidentService, AiContextBuilderService aiContextBuilderService) {
        this.incidentService = incidentService;
        this.aiContextBuilderService = aiContextBuilderService;
    }

    @GetMapping
    public List<IncidentResponse> listIncidents() {
        return incidentService.list();
    }

    @GetMapping("/{id}")
    public IncidentResponse getIncident(@PathVariable Long id) {
        return incidentService.get(id);
    }

    @GetMapping("/{id}/ai-context")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public AiIncidentContextResponse getAiContext(@PathVariable Long id) {
        return aiContextBuilderService.build(id);
    }

    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    public List<IncidentResponse> rebuildIncidents() {
        return incidentService.rebuild();
    }

    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public IncidentResponse acknowledge(@PathVariable Long id) {
        return incidentService.acknowledge(id);
    }

    @PatchMapping("/{id}/unacknowledge")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public IncidentResponse unacknowledge(@PathVariable Long id) {
        return incidentService.unacknowledge(id);
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public IncidentResponse resolve(@PathVariable Long id) {
        return incidentService.resolve(id);
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public IncidentResponse close(@PathVariable Long id) {
        return incidentService.resolve(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        incidentService.delete(id);
    }
}
