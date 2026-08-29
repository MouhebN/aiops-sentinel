package com.aiops.backend.pcap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(PcapCaptureProperties.class)
public class PcapCaptureConfig {

    @Bean(name = "packetCaptureExecutor")
    public Executor packetCaptureExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "packet-capture");
            thread.setDaemon(true);
            return thread;
        });
    }
}
