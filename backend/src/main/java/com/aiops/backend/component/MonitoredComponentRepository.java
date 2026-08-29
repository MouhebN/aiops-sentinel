package com.aiops.backend.component;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonitoredComponentRepository extends JpaRepository<MonitoredComponent, Long> {

    List<MonitoredComponent> findAllByOrderByCreatedAtDesc();

    List<MonitoredComponent> findAllByEnabledTrueOrderByCreatedAtDesc();

    Optional<MonitoredComponent> findByNameIgnoreCase(String name);

    Optional<MonitoredComponent> findFirstByIpAddressIgnoreCase(String ipAddress);

    boolean existsByIpAddressIgnoreCase(String ipAddress);

    boolean existsByIpAddressIgnoreCaseAndIdNot(String ipAddress, Long id);
}
