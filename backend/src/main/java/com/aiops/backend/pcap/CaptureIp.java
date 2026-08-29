package com.aiops.backend.pcap;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

final class CaptureIp {

    private static final Pattern IPV4 = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    );

    private CaptureIp() {
    }

    static String normalizeOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (address instanceof Inet4Address) {
                if (!IPV4.matcher(value).matches()) {
                    return null;
                }
                return address.getHostAddress();
            }
            if (address instanceof Inet6Address) {
                if (!value.contains(":")) {
                    return null;
                }
                return address.getHostAddress();
            }
            return null;
        } catch (UnknownHostException | IllegalArgumentException exception) {
            return null;
        }
    }
}
