package com.aiops.backend.component;

import com.aiops.backend.topology.TopologyIpExtractor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves a monitored component from any known IPv4 address.
 * Lookup order: {@link MonitoredComponent#getIpAddress()} then {@link ComponentNetworkInterface#getIpAddress()}.
 */
@Component
public class ComponentIdentityResolver {

    private final MonitoredComponentRepository componentRepository;
    private final ComponentNetworkInterfaceRepository interfaceRepository;

    public ComponentIdentityResolver(
            MonitoredComponentRepository componentRepository,
            ComponentNetworkInterfaceRepository interfaceRepository
    ) {
        this.componentRepository = componentRepository;
        this.interfaceRepository = interfaceRepository;
    }

    public Optional<ResolvedComponentIp> resolveByIp(String ip) {
        String normalized = TopologyIpExtractor.normalizeIp(ip);
        if (normalized == null) {
            return Optional.empty();
        }

        Optional<MonitoredComponent> primary = componentRepository.findFirstByIpAddressIgnoreCase(normalized);
        if (primary.isPresent()) {
            MonitoredComponent component = primary.get();
            Optional<ComponentNetworkInterface> matchingInterface = interfaceRepository
                    .findByMonitoredComponent_IdOrderByCreatedAtAsc(component.getId())
                    .stream()
                    .filter(item -> normalized.equalsIgnoreCase(item.getIpAddress()))
                    .findFirst();
            return Optional.of(new ResolvedComponentIp(
                    component,
                    normalized,
                    matchingInterface.map(ComponentNetworkInterface::getName).orElse(null),
                    matchingInterface.map(ComponentNetworkInterface::getRole).orElse(NetworkInterfaceRole.MANAGEMENT),
                    true
            ));
        }

        return interfaceRepository.findFirstByIpAddressIgnoreCase(normalized)
                .map(item -> new ResolvedComponentIp(
                        item.getMonitoredComponent(),
                        normalized,
                        item.getName(),
                        item.getRole(),
                        item.isPrimary()
                ));
    }

    public Optional<MonitoredComponent> resolveComponent(String ip) {
        return resolveByIp(ip).map(ResolvedComponentIp::component);
    }

    public record ResolvedComponentIp(
            MonitoredComponent component,
            String matchedIp,
            String matchedInterfaceName,
            NetworkInterfaceRole matchedRole,
            boolean matchedPrimary
    ) {
    }
}
