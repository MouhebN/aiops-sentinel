package com.aiops.backend.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/actuator/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "aiops-sentinel-backend",
                "timestamp", Instant.now().toString()
        );
    }
}
