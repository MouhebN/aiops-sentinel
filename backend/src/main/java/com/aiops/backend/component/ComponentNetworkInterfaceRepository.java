package com.aiops.backend.component;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComponentNetworkInterfaceRepository extends JpaRepository<ComponentNetworkInterface, Long> {

    List<ComponentNetworkInterface> findByMonitoredComponent_IdOrderByCreatedAtAsc(Long componentId);

    @EntityGraph(attributePaths = "monitoredComponent")
    Optional<ComponentNetworkInterface> findFirstByIpAddressIgnoreCase(String ipAddress);

    Optional<ComponentNetworkInterface> findByIdAndMonitoredComponent_Id(Long id, Long componentId);

    boolean existsByIpAddressIgnoreCase(String ipAddress);

    boolean existsByIpAddressIgnoreCaseAndIdNot(String ipAddress, Long id);

    boolean existsByIpAddressIgnoreCaseAndMonitoredComponent_IdNot(String ipAddress, Long componentId);

    void deleteByMonitoredComponent_Id(Long componentId);
}
