package com.aiops.backend.topology;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/topology")
public class TopologyController {

    private final TopologyService topologyService;
    private final ComponentRelationService relationService;

    public TopologyController(TopologyService topologyService, ComponentRelationService relationService) {
        this.topologyService = topologyService;
        this.relationService = relationService;
    }

    @GetMapping
    public TopologyResponse getTopology() {
        return topologyService.getTopology();
    }

    @GetMapping("/relations")
    public List<ComponentRelationResponse> listRelations() {
        return relationService.list();
    }

    @PostMapping("/relations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ComponentRelationResponse createRelation(@Valid @RequestBody SaveComponentRelationRequest request) {
        return relationService.create(request);
    }

    @DeleteMapping("/relations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public void deleteRelation(@PathVariable Long id) {
        relationService.delete(id);
    }
}
