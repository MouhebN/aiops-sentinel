package com.aiops.backend;

import com.aiops.backend.netflow.NfdumpCsvNetFlowRecordParser;
import com.aiops.backend.netflow.ParsedNetFlowRecord;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NfdumpCsvNetFlowRecordParserTests {

    private final NfdumpCsvNetFlowRecordParser parser = new NfdumpCsvNetFlowRecordParser();

    @Test
    void parsesRealNfdumpCsvOutput() {
        List<ParsedNetFlowRecord> records = parser.parse(List.of(
                "firstSeen,lastSeen,srcAddr,dstAddr,srcPort,dstPort,proto,packets,bytes,routerIP,input,output",
                "1786799706.624,1786799720.624,192.168.1.106,192.168.1.110,40000,21,6,6,900,172.22.0.5,1,2",
                "1786799705.624,1786799720.474,192.168.1.106,192.168.1.110,40001,22,6,9,1175,172.22.0.5,1,2"
        ));

        assertEquals(2, records.size());

        ParsedNetFlowRecord first = records.get(0);
        assertEquals(Instant.ofEpochSecond(1786799706L, 624_000_000L), first.startTime());
        assertEquals(Instant.ofEpochSecond(1786799720L, 624_000_000L), first.endTime());
        assertEquals("192.168.1.106", first.sourceIp());
        assertEquals("192.168.1.110", first.destinationIp());
        assertEquals(40000, first.sourcePort());
        assertEquals(21, first.destinationPort());
        assertEquals("TCP", first.protocol());
        assertEquals(6L, first.packets());
        assertEquals(900L, first.bytes());
        assertEquals("172.22.0.5", first.exporterName());
        assertEquals(1, first.inputInterface());
        assertEquals(2, first.outputInterface());
    }

    @Test
    void mapsNumericProtocols() {
        List<ParsedNetFlowRecord> records = parser.parse(List.of(
                "firstSeen,lastSeen,srcAddr,dstAddr,srcPort,dstPort,proto,packets,bytes,routerIP,input,output",
                "1786799706.624,1786799720.624,10.0.0.1,10.0.0.2,1111,53,17,1,100,172.22.0.5,1,2",
                "1786799706.624,1786799720.624,10.0.0.1,10.0.0.2,0,0,1,1,100,172.22.0.5,1,2",
                "1786799706.624,1786799720.624,10.0.0.1,10.0.0.2,1111,9999,132,1,100,172.22.0.5,1,2"
        ));

        assertEquals("UDP", records.get(0).protocol());
        assertEquals(1111, records.get(0).sourcePort());
        assertEquals(53, records.get(0).destinationPort());
        assertEquals("ICMP", records.get(1).protocol());
        assertNull(records.get(1).sourcePort());
        assertNull(records.get(1).destinationPort());
        assertEquals("UNKNOWN_132", records.get(2).protocol());
    }

    @Test
    void parsesIcmpEchoRequestTypeCodeAsIcmpNotAsPort() {
        ParsedNetFlowRecord record = parser.parse(List.of(
                "1787584534.741,1787584539.742,10.0.0.10,10.0.0.1,0,8.0,1,6,504,172.30.30.10,0,0"
        )).getFirst();

        assertEquals("ICMP", record.protocol());
        assertEquals("10.0.0.10", record.sourceIp());
        assertEquals("10.0.0.1", record.destinationIp());
        assertNull(record.sourcePort());
        assertNull(record.destinationPort());
        assertEquals(6L, record.packets());
        assertEquals(504L, record.bytes());
        assertTrue(record.rawRecord().contains("8.0"));
    }

    @Test
    void parsesIcmpEchoReplyTypeCodeAsIcmpNotAsPort() {
        ParsedNetFlowRecord record = parser.parse(List.of(
                "1787584534.741,1787584539.742,10.0.0.1,10.0.0.10,0,0.0,1,6,504,172.30.30.10,0,0"
        )).getFirst();

        assertEquals("ICMP", record.protocol());
        assertEquals("10.0.0.1", record.sourceIp());
        assertEquals("10.0.0.10", record.destinationIp());
        assertNull(record.sourcePort());
        assertNull(record.destinationPort());
        assertTrue(record.rawRecord().contains("0.0"));
    }

    @Test
    void skipsInvalidLinesAndKeepsValidOnes() {
        List<ParsedNetFlowRecord> records = parser.parse(List.of(
                "firstSeen,lastSeen,srcAddr,dstAddr,srcPort,dstPort,proto,packets,bytes,routerIP,input,output",
                "not-a-flow-line",
                "1786799706.624,1786799720.624,192.168.1.106,192.168.1.110,40000,21,6,6,900,172.22.0.5,1,2",
                "1786799706.624,broken,192.168.1.106,192.168.1.110,40000,22,6,6,900,172.22.0.5,1,2"
        ));

        assertEquals(1, records.size());
        assertEquals(21, records.getFirst().destinationPort());
    }

    @Test
    void returnsBadRequestWhenZeroValidRecordsRemain() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> parser.parse(List.of(
                "firstSeen,lastSeen,srcAddr,dstAddr,srcPort,dstPort,proto,packets,bytes,routerIP,input,output",
                "garbage",
                "1786799706.624,broken,192.168.1.106,192.168.1.110,40000,22,6,6,900,172.22.0.5,1,2"
        )));

        assertEquals(400, exception.getStatusCode().value());
        assertEquals("No valid NetFlow records found in nfdump output", exception.getReason());
    }

    @Test
    void rejectsDecimalPortOnTcp() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> parser.parse(List.of(
                "1787584534.741,1787584539.742,10.0.0.10,10.10.10.20,40000,8.0,6,1,64,172.30.30.10,0,0"
        )));
        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void epochSecondsAreUtcRegardlessOfJvmTimezone() {
        TimeZone previous = TimeZone.getDefault();
        Instant expected = Instant.parse("2026-08-22T15:43:10Z");
        String epoch = expected.getEpochSecond() + ".385";
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            ParsedNetFlowRecord record = parser.parse(List.of(
                    "firstSeen,lastSeen,srcAddr,dstAddr,srcPort,dstPort,proto,packets,bytes,routerIP,input,output",
                    epoch + "," + epoch + ",10.0.0.10,10.10.10.20,40000,22,6,1,64,10.0.0.1,1,2"
            )).getFirst();
            assertEquals(Instant.ofEpochSecond(expected.getEpochSecond(), 385_000_000L), record.startTime());
            assertEquals(ZoneId.of("America/New_York"), ZoneId.systemDefault());
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
