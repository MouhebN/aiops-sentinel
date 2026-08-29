package com.aiops.backend.syslog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyslogSourceRepository extends JpaRepository<SyslogSource, Long> {

    List<SyslogSource> findByEnabledTrueOrderByNameAsc();

    List<SyslogSource> findAllByOrderByNameAsc();

    Optional<SyslogSource> findFirstByEnabledTrueAndExpectedHostIgnoreCase(String expectedHost);

    Optional<SyslogSource> findFirstByEnabledTrueAndExpectedHostIgnoreCaseAndNameIgnoreCase(String expectedHost, String name);

    Optional<SyslogSource> findFirstByEnabledTrueAndExpectedHostIgnoreCaseAndDeviceNameIgnoreCase(String expectedHost, String deviceName);

    Optional<SyslogSource> findFirstByEnabledTrueAndNameIgnoreCase(String name);

    Optional<SyslogSource> findFirstByEnabledTrueAndDeviceNameIgnoreCase(String deviceName);
}
