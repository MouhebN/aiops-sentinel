package com.aiops.backend.syslog;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class UdpSyslogListener implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(UdpSyslogListener.class);

    private final SyslogIngestionService syslogIngestionService;
    private final boolean enabled;
    private final int udpPort;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "aiops-syslog-udp-listener");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService processingExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "aiops-syslog-udp-processor");
        thread.setDaemon(true);
        return thread;
    });

    private volatile DatagramSocket socket;

    public UdpSyslogListener(
            SyslogIngestionService syslogIngestionService,
            @Value("${app.syslog.enabled:true}") boolean enabled,
            @Value("${app.syslog.udp-port:5514}") int udpPort
    ) {
        this.syslogIngestionService = syslogIngestionService;
        this.enabled = enabled;
        this.udpPort = udpPort;
    }

    @Override
    public void start() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }

        executor.submit(this::listenLoop);
    }

    private void listenLoop() {
        try (DatagramSocket datagramSocket = new DatagramSocket(udpPort)) {
            socket = datagramSocket;
            datagramSocket.setReceiveBufferSize(262144);
            LOGGER.info("UDP syslog listener started on port {}", udpPort);
            byte[] buffer = new byte[8192];
            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    datagramSocket.receive(packet);
                    String sourceIp = packet.getAddress().getHostAddress();
                    byte[] payload = Arrays.copyOfRange(
                            packet.getData(),
                            packet.getOffset(),
                            packet.getOffset() + packet.getLength()
                    );
                    String rawMessage = new String(payload, StandardCharsets.UTF_8);
                    LOGGER.info(
                            "Received UDP syslog packet: sourceIp={}, bytes={}, rawLog={}",
                            sourceIp,
                            packet.getLength(),
                            rawMessage
                    );
                    processingExecutor.submit(() -> processPacket(sourceIp, rawMessage));
                } catch (Exception exception) {
                    if (running.get()) {
                        LOGGER.warn("Failed to receive syslog packet", exception);
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not bind UDP syslog listener on port {}: {}", udpPort, exception.getMessage());
        } finally {
            socket = null;
            running.set(false);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        DatagramSocket currentSocket = socket;
        if (currentSocket != null && !currentSocket.isClosed()) {
            currentSocket.close();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @PreDestroy
    public void shutdown() {
        stop();
        executor.shutdownNow();
        processingExecutor.shutdownNow();
    }

    private void processPacket(String sourceIp, String rawMessage) {
        try {
            String[] messages = rawMessage.split("\\R");
            for (String message : messages) {
                String normalized = message == null ? "" : message.trim();
                if (normalized.isBlank()) {
                    continue;
                }
                LOGGER.info(
                        "Dispatching syslog message from datagram: sourceIp={}, rawLog={}",
                        sourceIp,
                        normalized
                );
                syslogIngestionService.ingest(sourceIp, normalized);
            }
        } catch (Exception exception) {
            if (running.get()) {
                LOGGER.warn("Failed to process syslog packet from sourceIp={}", sourceIp, exception);
            }
        }
    }
}
