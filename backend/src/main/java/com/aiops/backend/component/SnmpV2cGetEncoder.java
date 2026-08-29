package com.aiops.backend.component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Builds SNMPv2c GetRequest PDUs. The community is a BER OCTET STRING; a blank
 * community encodes as a zero-length string, which tcpdump prints as SNMPv2c with no C=...
 * and which snmpd silently drops.
 */
final class SnmpV2cGetEncoder {

    private SnmpV2cGetEncoder() {
    }

    static String requireCommunity(String community) {
        if (community == null || community.isBlank()) {
            throw new IllegalArgumentException("SNMP community is not configured");
        }
        return community.trim();
    }

    static String maskCommunity(String community) {
        if (community == null || community.isBlank()) {
            return "(missing)";
        }
        String value = community.trim();
        if (value.length() == 1) {
            return "*";
        }
        return value.charAt(0) + "***";
    }

    static byte[] encodeGet(String community, String oid) throws IOException {
        String resolved = requireCommunity(community);
        byte[] version = encodeInteger(1); // SNMPv2c
        byte[] communityBytes = encodeOctetString(resolved.getBytes(StandardCharsets.UTF_8));
        byte[] requestId = encodeInteger(1);
        byte[] errorStatus = encodeInteger(0);
        byte[] errorIndex = encodeInteger(0);
        byte[] varbind = encodeSequence(encodeObjectIdentifier(oid), new byte[]{0x05, 0x00});
        byte[] varbindList = encodeSequence(varbind);
        byte[] pdu = encodeTlv((byte) 0xA0, concat(requestId, errorStatus, errorIndex, varbindList));
        return encodeSequence(version, communityBytes, pdu);
    }

    private static byte[] encodeInteger(int value) throws IOException {
        return new byte[]{0x02, 0x01, (byte) value};
    }

    private static byte[] encodeOctetString(byte[] value) throws IOException {
        return encodeTlv((byte) 0x04, value);
    }

    private static byte[] encodeObjectIdentifier(String oid) throws IOException {
        String[] parts = oid.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid SNMP OID");
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]));
        for (int i = 2; i < parts.length; i++) {
            writeBase128(body, Integer.parseInt(parts[i]));
        }
        return encodeTlv((byte) 0x06, body.toByteArray());
    }

    private static void writeBase128(ByteArrayOutputStream output, int value) {
        int stackSize = 0;
        int[] stack = new int[5];
        stack[stackSize++] = value & 0x7F;
        value >>= 7;
        while (value > 0) {
            stack[stackSize++] = 0x80 | (value & 0x7F);
            value >>= 7;
        }
        for (int i = stackSize - 1; i >= 0; i--) {
            output.write(stack[i]);
        }
    }

    private static byte[] encodeSequence(byte[]... values) throws IOException {
        return encodeTlv((byte) 0x30, concat(values));
    }

    private static byte[] concat(byte[]... values) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] value : values) {
            body.write(value);
        }
        return body.toByteArray();
    }

    private static byte[] encodeTlv(byte tag, byte[] value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(tag);
        writeLength(output, value.length);
        output.write(value);
        return output.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream output, int length) {
        if (length < 128) {
            output.write(length);
            return;
        }
        output.write(0x82);
        output.write((length >> 8) & 0xFF);
        output.write(length & 0xFF);
    }
}
