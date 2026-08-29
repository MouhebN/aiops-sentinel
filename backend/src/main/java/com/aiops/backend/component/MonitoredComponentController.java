package com.aiops.backend.component;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import com.aiops.backend.auth.AppUser;
import com.aiops.backend.auth.Role;
import com.aiops.backend.metric.MetricSampleResponse;
import com.aiops.backend.metric.MetricSampleService;
import com.aiops.backend.metric.MetricThresholdResponse;
import com.aiops.backend.metric.SaveMetricThresholdRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/components")
public class MonitoredComponentController {

    private final MonitoredComponentService service;
    private final ComponentNetworkInterfaceService interfaceService;
    private final MetricSampleService metricSampleService;
    private final AuditLogService auditLogService;

    public MonitoredComponentController(
            MonitoredComponentService service,
            ComponentNetworkInterfaceService interfaceService,
            MetricSampleService metricSampleService,
            AuditLogService auditLogService
    ) {
        this.service = service;
        this.interfaceService = interfaceService;
        this.metricSampleService = metricSampleService;
        this.auditLogService = auditLogService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public MonitoredComponentResponse create(@Valid @RequestBody SaveMonitoredComponentRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<MonitoredComponentResponse> list() {
        return service.list();
    }

    @GetMapping("/enabled")
    public List<MonitoredComponentResponse> listEnabled() {
        return service.listEnabled();
    }

    @GetMapping("/{id}")
    public MonitoredComponentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MonitoredComponentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody SaveMonitoredComponentRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/interfaces")
    public List<ComponentNetworkInterfaceResponse> listInterfaces(@PathVariable Long id) {
        return interfaceService.list(id);
    }

    @PostMapping("/{id}/interfaces")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ComponentNetworkInterfaceResponse createInterface(
            @PathVariable Long id,
            @Valid @RequestBody SaveComponentNetworkInterfaceRequest request
    ) {
        return interfaceService.create(id, request);
    }

    @PutMapping("/{id}/interfaces/{interfaceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ComponentNetworkInterfaceResponse updateInterface(
            @PathVariable Long id,
            @PathVariable Long interfaceId,
            @Valid @RequestBody SaveComponentNetworkInterfaceRequest request
    ) {
        return interfaceService.update(id, interfaceId, request);
    }

    @DeleteMapping("/{id}/interfaces/{interfaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteInterface(@PathVariable Long id, @PathVariable Long interfaceId) {
        interfaceService.delete(id, interfaceId);
    }

    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public MonitoredComponentResponse enable(@PathVariable Long id) {
        return service.enable(id);
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public MonitoredComponentResponse disable(@PathVariable Long id) {
        return service.disable(id);
    }

    @PatchMapping("/{id}/status")
    public MonitoredComponentResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateComponentStatusRequest request
    ) {
        return service.updateStatus(id, request);
    }

    @PostMapping("/{id}/check")
    public ComponentConnectionCheckResponse checkNow(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUser user
    ) {
        if (user == null || (user.getRole() != Role.ADMIN && user.getRole() != Role.OPERATOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manual check is not allowed");
        }
        return service.checkNow(id);
    }

    @GetMapping("/{id}/metrics")
    public List<MetricSampleResponse> listMetrics(
            @PathVariable Long id,
            @RequestParam(defaultValue = "24") int hours
    ) {
        return metricSampleService.listComponentSamples(id, hours);
    }

    @GetMapping("/{id}/thresholds")
    public List<MetricThresholdResponse> listThresholds(@PathVariable Long id) {
        return metricSampleService.listThresholds(id);
    }

    @PostMapping("/{id}/thresholds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public MetricThresholdResponse createThreshold(
            @PathVariable Long id,
            @Valid @RequestBody SaveMetricThresholdRequest request
    ) {
        MetricThresholdResponse response = metricSampleService.createThreshold(id, request);
        auditLogService.log(
                AuditAction.THRESHOLD_CREATED,
                "THRESHOLD",
                response.id().toString(),
                "Created threshold for metric " + response.metricName() + " on component " + id
        );
        return response;
    }

    @PutMapping("/{id}/thresholds/{thresholdId}")
    @PreAuthorize("hasRole('ADMIN')")
    public MetricThresholdResponse updateThreshold(
            @PathVariable Long id,
            @PathVariable Long thresholdId,
            @Valid @RequestBody SaveMetricThresholdRequest request
    ) {
        MetricThresholdResponse response = metricSampleService.updateThreshold(thresholdId, request);
        auditLogService.log(
                AuditAction.THRESHOLD_UPDATED,
                "THRESHOLD",
                response.id().toString(),
                "Updated threshold for metric " + response.metricName() + " on component " + id
        );
        return response;
    }

    @DeleteMapping("/{id}/thresholds/{thresholdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteThreshold(@PathVariable Long id, @PathVariable Long thresholdId) {
        auditLogService.log(
                AuditAction.THRESHOLD_DELETED,
                "THRESHOLD",
                thresholdId.toString(),
                "Deleted threshold on component " + id
        );
        metricSampleService.deleteThreshold(thresholdId);
    }
}
