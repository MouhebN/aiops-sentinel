package com.aiops.backend.netflow;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class NetFlowDataSeeder implements ApplicationRunner {

    private final NetFlowProperties properties;
    private final NetFlowSourceService sourceService;

    public NetFlowDataSeeder(NetFlowProperties properties, NetFlowSourceService sourceService) {
        this.properties = properties;
        this.sourceService = sourceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isEnabled()) {
            sourceService.ensureDefaultSource();
        }
    }
}
