package com.aiops.backend.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, String> {

    List<Device> findAllByOrderByLastSeenAtDesc();
}
