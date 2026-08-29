package com.aiops.backend.netflow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.netflow")
public class NetFlowProperties {

    private boolean enabled = true;
    private boolean autoImportEnabled = true;
    private long importIntervalMs = 15000L;
    private String nfdumpCommand = "nfdump";
    private String dataDir = "./runtime/netflow";
    private int collectorPort = 2055;
    private long highVolumeThresholdBytes = 104857600L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoImportEnabled() {
        return autoImportEnabled;
    }

    public void setAutoImportEnabled(boolean autoImportEnabled) {
        this.autoImportEnabled = autoImportEnabled;
    }

    public long getImportIntervalMs() {
        return importIntervalMs;
    }

    public void setImportIntervalMs(long importIntervalMs) {
        this.importIntervalMs = importIntervalMs;
    }

    public String getNfdumpCommand() {
        return nfdumpCommand;
    }

    public void setNfdumpCommand(String nfdumpCommand) {
        this.nfdumpCommand = nfdumpCommand;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public int getCollectorPort() {
        return collectorPort;
    }

    public void setCollectorPort(int collectorPort) {
        this.collectorPort = collectorPort;
    }

    public long getHighVolumeThresholdBytes() {
        return highVolumeThresholdBytes;
    }

    public void setHighVolumeThresholdBytes(long highVolumeThresholdBytes) {
        this.highVolumeThresholdBytes = highVolumeThresholdBytes;
    }
}
