package com.aiops.backend.component;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class ComponentNetworkInterfaceService {

    private static final Pattern IPV4 = Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");

    private final MonitoredComponentRepository componentRepository;
    private final ComponentNetworkInterfaceRepository interfaceRepository;
    private final AuditLogService auditLogService;

    public ComponentNetworkInterfaceService(
            MonitoredComponentRepository componentRepository,
            ComponentNetworkInterfaceRepository interfaceRepository,
            AuditLogService auditLogService
    ) {
        this.componentRepository = componentRepository;
        this.interfaceRepository = interfaceRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ComponentNetworkInterfaceResponse> list(Long componentId) {
        findComponent(componentId);
        return interfaceRepository.findByMonitoredComponent_IdOrderByCreatedAtAsc(componentId)
                .stream()
                .map(ComponentNetworkInterfaceResponse::from)
                .toList();
    }

    @Transactional
    public ComponentNetworkInterfaceResponse create(Long componentId, SaveComponentNetworkInterfaceRequest request) {
        MonitoredComponent component = findComponent(componentId);
        String ip = normalizeIp(request.ipAddress());
        assertIpAvailable(ip, componentId, null);
        boolean primary = Boolean.TRUE.equals(request.primary());
        if (primary) {
            clearPrimary(componentId);
        }
        ComponentNetworkInterface saved = interfaceRepository.save(new ComponentNetworkInterface(
                component,
                request.name().trim(),
                ip,
                request.role(),
                primary
        ));
        auditLogService.log(
                AuditAction.COMPONENT_UPDATED,
                "COMPONENT",
                componentId.toString(),
                "Added network interface " + saved.getName() + " (" + ip + ") on " + component.getName()
        );
        return ComponentNetworkInterfaceResponse.from(saved);
    }

    @Transactional
    public ComponentNetworkInterfaceResponse update(
            Long componentId,
            Long interfaceId,
            SaveComponentNetworkInterfaceRequest request
    ) {
        ComponentNetworkInterface networkInterface = interfaceRepository
                .findByIdAndMonitoredComponent_Id(interfaceId, componentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Network interface not found"));
        String ip = normalizeIp(request.ipAddress());
        assertIpAvailable(ip, componentId, interfaceId);
        boolean primary = Boolean.TRUE.equals(request.primary());
        if (primary) {
            clearPrimary(componentId);
        }
        networkInterface.update(request.name().trim(), ip, request.role(), primary);
        auditLogService.log(
                AuditAction.COMPONENT_UPDATED,
                "COMPONENT",
                componentId.toString(),
                "Updated network interface " + networkInterface.getName() + " (" + ip + ")"
        );
        return ComponentNetworkInterfaceResponse.from(networkInterface);
    }

    @Transactional
    public void delete(Long componentId, Long interfaceId) {
        ComponentNetworkInterface networkInterface = interfaceRepository
                .findByIdAndMonitoredComponent_Id(interfaceId, componentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Network interface not found"));
        interfaceRepository.delete(networkInterface);
        auditLogService.log(
                AuditAction.COMPONENT_UPDATED,
                "COMPONENT",
                componentId.toString(),
                "Deleted network interface " + networkInterface.getName() + " (" + networkInterface.getIpAddress() + ")"
        );
    }

    private void assertIpAvailable(String ip, Long componentId, Long interfaceId) {
        boolean takenByInterface = interfaceId == null
                ? interfaceRepository.existsByIpAddressIgnoreCase(ip)
                : interfaceRepository.existsByIpAddressIgnoreCaseAndIdNot(ip, interfaceId);
        if (takenByInterface) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IP address is already assigned to another interface");
        }
        if (componentRepository.existsByIpAddressIgnoreCaseAndIdNot(ip, componentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IP address is already the primary address of another component");
        }
    }

    private void clearPrimary(Long componentId) {
        interfaceRepository.findByMonitoredComponent_IdOrderByCreatedAtAsc(componentId)
                .forEach(item -> item.setPrimary(false));
    }

    private MonitoredComponent findComponent(Long componentId) {
        return componentRepository.findById(componentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitored component not found"));
    }

    private String normalizeIp(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP address is required");
        }
        String trimmed = value.trim();
        if (!IPV4.matcher(trimmed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP address must be IPv4");
        }
        return trimmed;
    }
}
