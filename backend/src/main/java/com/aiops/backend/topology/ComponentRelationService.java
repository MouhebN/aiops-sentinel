package com.aiops.backend.topology;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import com.aiops.backend.component.MonitoredComponent;
import com.aiops.backend.component.MonitoredComponentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ComponentRelationService {

    private final ComponentRelationRepository relationRepository;
    private final MonitoredComponentRepository componentRepository;
    private final AuditLogService auditLogService;

    public ComponentRelationService(
            ComponentRelationRepository relationRepository,
            MonitoredComponentRepository componentRepository,
            AuditLogService auditLogService
    ) {
        this.relationRepository = relationRepository;
        this.componentRepository = componentRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ComponentRelationResponse> list() {
        return relationRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(ComponentRelationResponse::from)
                .toList();
    }

    @Transactional
    public ComponentRelationResponse create(SaveComponentRelationRequest request) {
        if (request.sourceComponentId().equals(request.targetComponentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A component cannot connect to itself");
        }
        MonitoredComponent source = findComponent(request.sourceComponentId());
        MonitoredComponent target = findComponent(request.targetComponentId());
        ComponentRelationType type = request.relationType();
        if (relationRepository.existsBySource_IdAndTarget_IdAndRelationType(source.getId(), target.getId(), type)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This connection already exists");
        }
        String label = request.label() == null || request.label().isBlank() ? null : request.label().trim();
        ComponentRelation saved = relationRepository.save(new ComponentRelation(source, target, type, label));
        auditLogService.log(
                AuditAction.COMPONENT_RELATION_CREATED,
                "COMPONENT_RELATION",
                saved.getId().toString(),
                "Connected " + source.getName() + " to " + target.getName() + " (" + type + ")"
        );
        return ComponentRelationResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        ComponentRelation relation = relationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));
        auditLogService.log(
                AuditAction.COMPONENT_RELATION_DELETED,
                "COMPONENT_RELATION",
                id.toString(),
                "Removed connection " + relation.getSource().getName() + " -> " + relation.getTarget().getName()
        );
        relationRepository.delete(relation);
    }

    private MonitoredComponent findComponent(Long id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitored component not found"));
    }
}
