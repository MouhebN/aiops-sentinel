package com.aiops.backend.netflow;

import com.aiops.backend.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class NetworkFlowController {
    private final NetworkFlowRepository networkFlowRepository;
    private final NetFlowImportService importService;

    public NetworkFlowController(NetworkFlowRepository networkFlowRepository, NetFlowImportService importService) {
        this.networkFlowRepository = networkFlowRepository;
        this.importService = importService;
    }

    @GetMapping("/netflow/flows")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OPERATOR','ROLE_VIEWER')")
    public PageResponse<NetworkFlowResponse> listFlows(
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String destinationIp,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) Boolean suspicious,
            @RequestParam(required = false) NetFlowAnomalyType anomalyType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Specification<NetworkFlow> specification = (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (sourceIp != null && !sourceIp.isBlank()) {
                predicates.add(builder.equal(root.get("sourceIp"), sourceIp.trim()));
            }
            if (destinationIp != null && !destinationIp.isBlank()) {
                predicates.add(builder.equal(root.get("destinationIp"), destinationIp.trim()));
            }
            if (protocol != null && !protocol.isBlank()) {
                predicates.add(builder.equal(builder.upper(root.get("protocol")), protocol.trim().toUpperCase()));
            }
            if (suspicious != null) {
                predicates.add(builder.equal(root.get("suspicious"), suspicious));
            }
            if (anomalyType != null) {
                predicates.add(builder.equal(root.get("anomalyType"), anomalyType));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("startTime"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("endTime"), to));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return PageResponse.from(
                networkFlowRepository.findAll(
                                specification,
                                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"))
                        )
                        .map(NetworkFlowResponse::from)
        );
    }

    @GetMapping("/incidents/{id}/network-flows")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OPERATOR','ROLE_VIEWER')")
    public List<NetworkFlowResponse> listIncidentFlows(@PathVariable Long id) {
        List<NetworkFlowResponse> flows = networkFlowRepository.findByIncidentIdOrderByStartTimeDesc(id).stream()
                .filter(flow -> id.equals(flow.getIncidentId()))
                .map(NetworkFlowResponse::from)
                .toList();
        return flows;
    }

    @GetMapping("/netflow/import-runs")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OPERATOR','ROLE_VIEWER')")
    public List<NetFlowImportRunResponse> listImportRuns() {
        return importService.listRuns();
    }

    @PostMapping("/netflow/import/latest")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OPERATOR')")
    public NetFlowImportRunResponse importLatest(@RequestParam Long sourceId) {
        return importService.importLatest(sourceId);
    }

    @PostMapping("/netflow/import/sample")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OPERATOR')")
    public NetFlowImportRunResponse importSample() {
        return importService.importSample();
    }
}
