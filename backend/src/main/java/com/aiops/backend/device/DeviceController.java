package com.aiops.backend.device;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceRepository deviceRepository;

    public DeviceController(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @GetMapping
    public List<DeviceResponse> listDevices() {
        return deviceRepository.findAllByOrderByLastSeenAtDesc()
                .stream()
                .map(DeviceResponse::from)
                .toList();
    }
}
