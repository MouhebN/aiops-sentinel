package com.aiops.backend.topology;

import com.aiops.backend.event.Event;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TopologyIpExtractor {

    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern LABELED = Pattern.compile(
            "(?i)\\b(sourceAddress|destinationAddress|SRC|DST|src|dst)\\s*[=:]\\s*(\\d{1,3}(?:\\.\\d{1,3}){3})"
    );
    private static final Pattern ARROW = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d{1,3}){3})\\s*(?::\\d+)?\\s*(?:->|→)\\s*(\\d{1,3}(?:\\.\\d{1,3}){3})"
    );

    private TopologyIpExtractor() {
    }

    public static String sourceIp(Event event) {
        String labeled = labeledValue(join(event), true);
        if (labeled != null) {
            return labeled;
        }
        Matcher arrow = ARROW.matcher(join(event));
        if (arrow.find()) {
            return arrow.group(1);
        }
        if (event.getSourceIp() != null && looksLikeIp(event.getSourceIp())) {
            return event.getSourceIp().trim();
        }
        return null;
    }

    public static String destinationIp(Event event) {
        String labeled = labeledValue(join(event), false);
        if (labeled != null) {
            return labeled;
        }
        Matcher arrow = ARROW.matcher(join(event));
        if (arrow.find()) {
            return arrow.group(2);
        }
        return null;
    }

    public static boolean looksLikeIp(String value) {
        return value != null && IPV4.matcher(value.trim()).matches();
    }

    public static String normalizeIp(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return looksLikeIp(trimmed) ? trimmed : null;
    }

    public static String firstIp(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = IPV4.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    public static String sourceFromCorrelationKey(String correlationKey) {
        if (correlationKey == null) {
            return null;
        }
        String[] parts = correlationKey.split(":");
        if (parts.length >= 4 && "NETFLOW".equalsIgnoreCase(parts[0])) {
            return normalizeIp(parts[2]);
        }
        return null;
    }

    public static String destinationFromCorrelationKey(String correlationKey) {
        if (correlationKey == null) {
            return null;
        }
        String[] parts = correlationKey.split(":");
        if (parts.length >= 4 && "NETFLOW".equalsIgnoreCase(parts[0])) {
            return normalizeIp(parts[3]);
        }
        return null;
    }

    private static String labeledValue(String text, boolean source) {
        Matcher matcher = LABELED.matcher(text);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            boolean isSource = key.contains("source") || "src".equals(key);
            boolean isDest = key.contains("destination") || "dst".equals(key);
            if (source && isSource) {
                return matcher.group(2);
            }
            if (!source && isDest) {
                return matcher.group(2);
            }
        }
        return null;
    }

    private static String join(Event event) {
        StringBuilder builder = new StringBuilder();
        if (event.getDetails() != null) {
            builder.append(event.getDetails()).append('\n');
        }
        if (event.getRawLog() != null) {
            builder.append(event.getRawLog()).append('\n');
        }
        if (event.getMessage() != null) {
            builder.append(event.getMessage());
        }
        return builder.toString();
    }
}
