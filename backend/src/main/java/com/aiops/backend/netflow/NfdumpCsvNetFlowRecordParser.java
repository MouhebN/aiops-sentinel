package com.aiops.backend.netflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class NfdumpCsvNetFlowRecordParser implements NetFlowRecordParser {
    private static final Logger log = LoggerFactory.getLogger(NfdumpCsvNetFlowRecordParser.class);
    private static final String HEADER = "firstSeen,lastSeen,srcAddr,dstAddr,srcPort,dstPort,proto,packets,bytes,routerIP,input,output";

    @Override
    public List<ParsedNetFlowRecord> parse(List<String> lines) {
        List<ParsedNetFlowRecord> records = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (isHeaderLine(trimmed)) {
                continue;
            }
            String[] parts = trimmed.split(",", -1);
            if (parts.length < 12) {
                warn(index + 1, trimmed, "not enough CSV columns");
                continue;
            }
            try {
                Instant startTime = parseEpoch(parts[0].trim());
                Instant endTime = parseEpoch(parts[1].trim());
                String protocol = normalizeProtocol(parts[6]);
                Integer sourcePort;
                Integer destinationPort;
                if (isIcmp(protocol)) {
                    // nfdump %sp/%dp for ICMP are type.code (e.g. 8.0 echo request, 0.0 echo reply),
                    // not TCP/UDP ports. Keep type/code in rawRecord only.
                    sourcePort = null;
                    destinationPort = null;
                } else {
                    sourcePort = parseInteger(parts[4]);
                    destinationPort = parseInteger(parts[5]);
                }
                records.add(new ParsedNetFlowRecord(
                        startTime,
                        endTime,
                        valueOrNull(parts[2]),
                        valueOrNull(parts[3]),
                        sourcePort,
                        destinationPort,
                        protocol,
                        parseLong(parts[7]),
                        parseLong(parts[8]),
                        valueOrNull(parts[9]),
                        parseInteger(parts[10]),
                        parseInteger(parts[11]),
                        trimmed
                ));
            } catch (RuntimeException exception) {
                warn(index + 1, trimmed, exception.getMessage());
            }
        }
        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid NetFlow records found in nfdump output");
        }
        return records;
    }

    /**
     * nfdump {@code %tsr}/{@code %ter} are seconds since the Unix epoch (UTC).
     * Do not interpret these as local wall-clock strings; JVM timezone must not shift them.
     */
    private Instant parseEpoch(String value) {
        BigDecimal epoch = new BigDecimal(value.trim());
        long seconds = epoch.longValue();
        BigDecimal fractional = epoch.subtract(BigDecimal.valueOf(seconds));
        long nanos = fractional.movePointRight(9).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private Integer parseInteger(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return Integer.valueOf(trimmed);
    }

    private long parseLong(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            return 0L;
        }
        return Long.parseLong(trimmed);
    }

    private String valueOrNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeProtocol(String value) {
        String trimmed = valueOrNull(value);
        if (trimmed == null) {
            return "UNKNOWN";
        }
        return switch (trimmed) {
            case "6" -> "TCP";
            case "17" -> "UDP";
            case "1" -> "ICMP";
            default -> isNumeric(trimmed) ? "UNKNOWN_" + trimmed : trimmed.toUpperCase(Locale.ROOT);
        };
    }

    private boolean isIcmp(String protocol) {
        return "ICMP".equalsIgnoreCase(protocol);
    }

    private boolean isHeaderLine(String value) {
        return HEADER.equalsIgnoreCase(value)
                || value.startsWith("tsr,")
                || value.startsWith("start")
                || value.toLowerCase(Locale.ROOT).startsWith("firstseen,lastseen,srcaddr,dstaddr");
    }

    private boolean isNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private void warn(int lineNumber, String rawLine, String reason) {
        log.warn("Skipping invalid NetFlow line {} reason={} raw={}", lineNumber, reason, rawLine);
    }
}
