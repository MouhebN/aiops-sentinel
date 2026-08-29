package com.aiops.backend.pcap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.pcap")
public class PcapCaptureProperties {

    private Capture capture = new Capture();
    private AutoCapture auto = new AutoCapture();
    private Rolling rolling = new Rolling();
    private LabSensor labSensor = new LabSensor();
    private Map<String, Point> points = new LinkedHashMap<>();

    public Capture getCapture() {
        return capture;
    }

    public void setCapture(Capture capture) {
        this.capture = capture;
    }

    public AutoCapture getAuto() {
        return auto;
    }

    public void setAuto(AutoCapture auto) {
        this.auto = auto;
    }

    public Rolling getRolling() {
        return rolling;
    }

    public void setRolling(Rolling rolling) {
        this.rolling = rolling;
    }

    public LabSensor getLabSensor() {
        return labSensor;
    }

    public void setLabSensor(LabSensor labSensor) {
        this.labSensor = labSensor;
    }

    public Map<String, Point> getPoints() {
        return points;
    }

    public void setPoints(Map<String, Point> points) {
        this.points = points;
    }

    public static class Capture {
        private int defaultDurationSeconds = 20;
        private int maxDurationSeconds = 60;
        private int minDurationSeconds = 5;
        private long maxFileSizeBytes = 20L * 1024 * 1024;
        private int maxConcurrent = 1;
        private String defaultPoint = "bank-firewall-wan";
        private boolean async = true;

        public int getDefaultDurationSeconds() {
            return defaultDurationSeconds;
        }

        public void setDefaultDurationSeconds(int defaultDurationSeconds) {
            this.defaultDurationSeconds = defaultDurationSeconds;
        }

        public int getMaxDurationSeconds() {
            return maxDurationSeconds;
        }

        public void setMaxDurationSeconds(int maxDurationSeconds) {
            this.maxDurationSeconds = maxDurationSeconds;
        }

        public int getMinDurationSeconds() {
            return minDurationSeconds;
        }

        public void setMinDurationSeconds(int minDurationSeconds) {
            this.minDurationSeconds = minDurationSeconds;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }

        public String getDefaultPoint() {
            return defaultPoint;
        }

        public void setDefaultPoint(String defaultPoint) {
            this.defaultPoint = defaultPoint;
        }

        public boolean isAsync() {
            return async;
        }

        public void setAsync(boolean async) {
            this.async = async;
        }
    }

    public static class AutoCapture {
        private boolean enabled = true;
        private int durationSeconds = 20;
        private String categories = "SECURITY";
        private String signalTokens = "PORT_SCAN,SUSPICIOUS,RECON";
        private String minSeverity = "WARNING";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public String getCategories() {
            return categories;
        }

        public void setCategories(String categories) {
            this.categories = categories;
        }

        public String getSignalTokens() {
            return signalTokens;
        }

        public void setSignalTokens(String signalTokens) {
            this.signalTokens = signalTokens;
        }

        public String getMinSeverity() {
            return minSeverity;
        }

        public void setMinSeverity(String minSeverity) {
            this.minSeverity = minSeverity;
        }
    }

    public static class Rolling {
        private boolean enabled = true;
        private int preTriggerSeconds = 60;
        private int postTriggerSeconds = 20;
        private int segmentSeconds = 5;
        private int maxFileSizeMb = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPreTriggerSeconds() {
            return preTriggerSeconds;
        }

        public void setPreTriggerSeconds(int preTriggerSeconds) {
            this.preTriggerSeconds = preTriggerSeconds;
        }

        public int getPostTriggerSeconds() {
            return postTriggerSeconds;
        }

        public void setPostTriggerSeconds(int postTriggerSeconds) {
            this.postTriggerSeconds = postTriggerSeconds;
        }

        public int getSegmentSeconds() {
            return segmentSeconds;
        }

        public void setSegmentSeconds(int segmentSeconds) {
            this.segmentSeconds = segmentSeconds;
        }

        public int getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(int maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }
    }

    public static class LabSensor {
        private String baseUrl = "http://172.30.30.10:8090";
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 8000;
        private int fileReadTimeoutMs = 15000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getFileReadTimeoutMs() {
            return fileReadTimeoutMs;
        }

        public void setFileReadTimeoutMs(int fileReadTimeoutMs) {
            this.fileReadTimeoutMs = fileReadTimeoutMs;
        }
    }

    public static class Point {
        private String provider = "LAB_SENSOR";
        private String displayName = "BANK-FW-01 / WAN";
        private String interfaceName = "eth1";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public void setInterfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
        }
    }
}
