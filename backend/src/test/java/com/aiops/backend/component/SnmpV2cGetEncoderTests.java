package com.aiops.backend.component;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnmpV2cGetEncoderTests {

    @Test
    void encodeGetPutsConfiguredCommunityInPdu() throws Exception {
        byte[] pdu = SnmpV2cGetEncoder.encodeGet("banklab", "1.3.6.1.2.1.1.1.0");
        String ascii = new String(pdu, StandardCharsets.US_ASCII);

        assertThat(ascii).contains("banklab");
        assertThat(ascii).doesNotContain("public");
        assertThat(indexOfOctetString(pdu, "banklab")).isGreaterThan(0);
    }

    @Test
    void encodeGetRejectsBlankCommunity() {
        assertThatThrownBy(() -> SnmpV2cGetEncoder.encodeGet("  ", "1.3.6.1.2.1.1.1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SNMP community is not configured");
        assertThatThrownBy(() -> SnmpV2cGetEncoder.encodeGet(null, "1.3.6.1.2.1.1.1.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maskCommunityDoesNotLogFullValue() {
        assertThat(SnmpV2cGetEncoder.maskCommunity("banklab")).isEqualTo("b***");
        assertThat(SnmpV2cGetEncoder.maskCommunity("banklab")).doesNotContain("banklab");
    }

    private static int indexOfOctetString(byte[] pdu, String community) {
        byte[] needle = community.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= pdu.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (pdu[i + j] != needle[j]) {
                    continue outer;
                }
            }
            if (i >= 2 && (pdu[i - 2] & 0xFF) == 0x04 && (pdu[i - 1] & 0xFF) == needle.length) {
                return i;
            }
        }
        return -1;
    }
}
