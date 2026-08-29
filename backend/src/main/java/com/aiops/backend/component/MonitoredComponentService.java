package com.aiops.backend.component;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import com.aiops.backend.metric.MetricThresholdRepository;
import com.aiops.backend.topology.ComponentRelationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class MonitoredComponentService {

    private static final int DEFAULT_CHECK_INTERVAL_SECONDS = 30;

    private final MonitoredComponentRepository repository;
    private final ComponentCheckService checkService;
    private final AuditLogService auditLogService;
    private final ComponentRelationRepository relationRepository;
    private final MetricThresholdRepository thresholdRepository;
    private final ComponentNetworkInterfaceRepository interfaceRepository;

    public MonitoredComponentService(
            MonitoredComponentRepository repository,
            ComponentCheckService checkService,
            AuditLogService auditLogService,
            ComponentRelationRepository relationRepository,
            MetricThresholdRepository thresholdRepository,
            ComponentNetworkInterfaceRepository interfaceRepository
    ) {
        this.repository = repository;
        this.checkService = checkService;
        this.auditLogService = auditLogService;
        this.relationRepository = relationRepository;
        this.thresholdRepository = thresholdRepository;
        this.interfaceRepository = interfaceRepository;
    }

    @Transactional
    public MonitoredComponentResponse create(SaveMonitoredComponentRequest request) {
        String ipAddress = clean(request.ipAddress());
        assertPrimaryIpAvailable(ipAddress, null);
        MonitoredComponent component = new MonitoredComponent(
                request.name(),
                request.type(),
                ipAddress,
                clean(request.httpUrl()),
                request.tcpPort(),
                request.snmpPort() == null ? 161 : request.snmpPort(),
                clean(request.snmpCommunity()),
                valueOrDefault(clean(request.snmpOid()), "1.3.6.1.2.1.1.1.0"),
                request.location(),
                request.criticality() == null ? Criticality.MEDIUM : request.criticality(),
                request.monitoringMethods(),
                intervalOrDefault(request.checkIntervalSeconds()),
                request.enabled() == null || request.enabled()
        );

        MonitoredComponent saved = repository.save(component);
        auditLogService.log(
                AuditAction.COMPONENT_CREATED,
                "COMPONENT",
                saved.getId().toString(),
                "Created component " + saved.getName()
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MonitoredComponentResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonitoredComponentResponse> listEnabled() {
        return repository.findAllByEnabledTrueOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitoredComponentResponse get(Long id) {
        return toResponse(findById(id));
    }

    @Transactional
    public MonitoredComponentResponse update(Long id, SaveMonitoredComponentRequest request) {
        MonitoredComponent component = findById(id);
        String ipAddress = clean(request.ipAddress());
        assertPrimaryIpAvailable(ipAddress, id);
        boolean wasEnabled = component.isEnabled();
        boolean monitoringEnabled = request.enabled() == null ? wasEnabled : request.enabled();
        String snmpCommunity = clean(request.snmpCommunity());
        if (snmpCommunity == null) {
            snmpCommunity = component.getSnmpCommunity();
        }
        component.updateConfiguration(
                request.name(),
                request.type(),
                ipAddress,
                clean(request.httpUrl()),
                request.tcpPort(),
                request.snmpPort() == null ? 161 : request.snmpPort(),
                snmpCommunity,
                valueOrDefault(clean(request.snmpOid()), "1.3.6.1.2.1.1.1.0"),
                request.location(),
                request.criticality() == null ? Criticality.MEDIUM : request.criticality(),
                request.monitoringMethods(),
                intervalOrDefault(request.checkIntervalSeconds()),
                monitoringEnabled
        );
        if (!wasEnabled && monitoringEnabled) {
            component.enable();
        }
        if (wasEnabled && !monitoringEnabled) {
            component.disable();
        }
        auditLogService.log(
                AuditAction.COMPONENT_UPDATED,
                "COMPONENT",
                component.getId().toString(),
                "Updated component " + component.getName()
        );
        return toResponse(component);
    }

    @Transactional
    public void delete(Long id) {
        MonitoredComponent component = findById(id);
        component.disable();
        repository.saveAndFlush(component);
        auditLogService.log(
                AuditAction.COMPONENT_DELETED,
                "COMPONENT",
                component.getId().toString(),
                "Deleted component " + component.getName()
        );
        relationRepository.deleteBySource_IdOrTarget_Id(id, id);
        thresholdRepository.deleteByComponentId(id);
        interfaceRepository.deleteByMonitoredComponent_Id(id);
        repository.delete(component);
        repository.flush();
    }

    @Transactional
    public MonitoredComponentResponse enable(Long id) {
        MonitoredComponent component = findById(id);
        component.enable();
        repository.saveAndFlush(component);
        auditLogService.log(
                AuditAction.MONITORING_STARTED,
                "COMPONENT",
                component.getId().toString(),
                "Started monitoring for " + component.getName()
        );
        return toResponse(component);
    }

    @Transactional
    public MonitoredComponentResponse disable(Long id) {
        MonitoredComponent component = findById(id);
        component.disable();
        repository.saveAndFlush(component);
        auditLogService.log(
                AuditAction.MONITORING_STOPPED,
                "COMPONENT",
                component.getId().toString(),
                "Stopped monitoring for " + component.getName()
        );
        return toResponse(component);
    }

    @Transactional
    public MonitoredComponentResponse updateStatus(Long id, UpdateComponentStatusRequest request) {
        MonitoredComponent component = findById(id);
        if (!ComponentMonitoringPolicy.shouldAcceptExternalStatusUpdate(component)) {
            return toResponse(component);
        }
        Instant checkedAt = request.checkedAt() == null ? Instant.now() : request.checkedAt();
        Instant seenAt = request.status() == ComponentStatus.UP
                ? request.seenAt() == null ? checkedAt : request.seenAt()
                : request.seenAt();
        component.updateStatus(request.status(), checkedAt, seenAt, clean(request.error()), clean(request.error()));
        return toResponse(component);
    }

    @Transactional
    public ComponentConnectionCheckResponse checkNow(Long id) {
        MonitoredComponent component = findById(id);
        boolean monitoringEnabled = component.isEnabled();
        ComponentConnectionCheckResponse response = checkService.testConnection(component, Instant.now());
        if (!monitoringEnabled && component.isEnabled()) {
            component.disable();
            repository.saveAndFlush(component);
        }
        auditLogService.log(
                AuditAction.MANUAL_CHECK_TRIGGERED,
                "COMPONENT",
                component.getId().toString(),
                response.message() + (monitoringEnabled ? "" : " (monitoring remains stopped)")
        );
        return response;
    }

    private MonitoredComponent findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitored component not found"));
    }

    private void assertPrimaryIpAvailable(String ip, Long componentId) {
        if (ip == null) {
            return;
        }
        boolean takenByOtherInterface = componentId == null
                ? interfaceRepository.existsByIpAddressIgnoreCase(ip)
                : interfaceRepository.existsByIpAddressIgnoreCaseAndMonitoredComponent_IdNot(ip, componentId);
        if (takenByOtherInterface) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "IP address is already assigned to another component interface"
            );
        }
    }

    private MonitoredComponentResponse toResponse(MonitoredComponent component) {
        return MonitoredComponentResponse.from(
                component,
                interfaceRepository.findByMonitoredComponent_IdOrderByCreatedAtAsc(component.getId())
                        .stream()
                        .map(ComponentNetworkInterfaceResponse::from)
                        .toList()
        );
    }

    private int intervalOrDefault(Integer interval) {
        return interval == null ? DEFAULT_CHECK_INTERVAL_SECONDS : interval;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
