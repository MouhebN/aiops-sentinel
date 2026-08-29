package com.aiops.backend;

import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.netflow.NfdumpCsvNetFlowRecordParser;
import com.aiops.backend.netflow.NetFlowAnomalyDetectionService;
import com.aiops.backend.netflow.NetworkFlow;
import com.aiops.backend.netflow.NetworkFlowRepository;
import com.aiops.backend.netflow.ParsedNetFlowRecord;
import com.aiops.backend.syslog.SaveSyslogSourceRequest;
import com.aiops.backend.syslog.SyslogIngestionService;
import com.aiops.backend.syslog.SyslogParserProfile;
import com.aiops.backend.syslog.SyslogSource;
import com.aiops.backend.syslog.SyslogSourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SyslogNetFlowTimelineTests {

    private static final ZoneId DEPLOYMENT_ZONE = ZoneId.of("Africa/Tunis");
    private static final DateTimeFormatter RFC3164 = DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.ENGLISH);

    @Autowired
    private SyslogIngestionService syslogIngestionService;

    @Autowired
    private SyslogSourceRepository syslogSourceRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private NetworkFlowRepository networkFlowRepository;

    @Autowired
    private NetFlowAnomalyDetectionService anomalyDetectionService;

    @Test
    void syslogDenyAndNetFlowTenMinutesApartStayTenMinutesApartAfterIngestion() {
        String host = uniqueHost();
        saveFirewallSource(host);
        Instant syslogUtc = tenMinutesAgo().minus(Duration.ofMinutes(10));
        Instant netflowUtc = syslogUtc.plus(Duration.ofMinutes(10));

        String stamp = RFC3164.withZone(DEPLOYMENT_ZONE).format(syslogUtc);
        syslogIngestionService.ingest("203.0.113.77", denyLog(stamp, host, "10.77.10.10", "10.77.10.20", 22));

        Event syslogEvent = eventFor(host);
        ParsedNetFlowRecord netflowRecord = nfdumpRecord(netflowUtc, "10.77.10.10", "10.77.10.20", 22);

        Duration gap = Duration.between(syslogEvent.getOccurredAt(), netflowRecord.startTime());
        assertThat(syslogEvent.getOccurredAt()).isEqualTo(syslogUtc);
        assertThat(netflowRecord.startTime()).isEqualTo(netflowUtc);
        assertThat(gap.toMinutes()).isEqualTo(10);
        assertThat(gap.abs().toMinutes()).isLessThan(30);
        assertThat(gap.toMinutes()).isNotEqualTo(70);
    }

    @Test
    void sameSrcDstInsideThirtyMinutesCorrelatesOntoSyslogIncident() {
        String host = uniqueHost();
        saveFirewallSource(host);
        Instant syslogUtc = tenMinutesAgo().minus(Duration.ofMinutes(10));
        Instant netflowUtc = syslogUtc.plus(Duration.ofMinutes(10));

        String stamp = RFC3164.withZone(DEPLOYMENT_ZONE).format(syslogUtc);
        syslogIngestionService.ingest("203.0.113.77", denyLog(stamp, host, "10.77.11.10", "10.77.11.20", 22));

        Event syslogEvent = eventFor(host);
        Incident syslogIncident = incidentRepository.findAll().stream()
                .filter(incident -> host.equals(incident.getDeviceId()))
                .findFirst()
                .orElseThrow();

        List<NetworkFlow> scan = savePortScan("10.77.11.10", "10.77.11.20", netflowUtc, 21, 22, 23, 80, 443, 3306, 5432);
        anomalyDetectionService.analyze(null, scan);

        Set<Long> linked = scan.stream()
                .map(flow -> networkFlowRepository.findById(flow.getId()).orElseThrow().getIncidentId())
                .collect(Collectors.toSet());
        assertThat(linked).containsExactly(syslogIncident.getId());
        assertThat(Duration.between(syslogEvent.getOccurredAt(), netflowUtc).toMinutes()).isEqualTo(10);
    }

    @Test
    void timezoneConversionDoesNotIntroduceOneHourOffsetWhenSenderUsesParserZone() {
        String host = uniqueHost();
        saveFirewallSource(host);
        Instant realEvent = tenMinutesAgo();
        String stamp = RFC3164.withZone(DEPLOYMENT_ZONE).format(realEvent);
        syslogIngestionService.ingest("203.0.113.77", denyLog(stamp, host, "10.77.12.10", "10.77.12.20", 22));

        Event syslogEvent = eventFor(host);
        ParsedNetFlowRecord netflowRecord = nfdumpRecord(
                realEvent.plus(Duration.ofMinutes(10)),
                "10.77.12.10",
                "10.77.12.20",
                443
        );

        assertThat(syslogEvent.getOccurredAt()).isEqualTo(realEvent);
        assertThat(Duration.between(syslogEvent.getOccurredAt(), netflowRecord.startTime()).toMinutes()).isEqualTo(10);
        assertThat(Duration.between(syslogEvent.getOccurredAt(), netflowRecord.startTime()).abs().toMinutes())
                .isNotEqualTo(70);
    }

    private void saveFirewallSource(String host) {
        syslogSourceRepository.save(new SyslogSource(new SaveSyslogSourceRequest(
                host,
                host,
                host,
                host,
                DeviceType.FIREWALL,
                "lab",
                SyslogParserProfile.FIREWALL,
                true
        )));
    }

    private Event eventFor(String host) {
        return eventRepository.findAll().stream()
                .filter(saved -> host.equals(saved.getDeviceId()) || host.equals(saved.getDeviceName()))
                .findFirst()
                .orElseThrow();
    }

    private String denyLog(String stamp, String host, String src, String dst, int dpt) {
        return "<134>" + stamp + " " + host
                + " firewall: DENY SRC=" + src + " DST=" + dst + " DPT=" + dpt
                + " PROTO=TCP DENY TCP " + src + ":45122 -> " + dst + ":" + dpt;
    }

    private ParsedNetFlowRecord nfdumpRecord(Instant start, String src, String dst, int dpt) {
        String epoch = start.getEpochSecond() + ".000";
        String line = epoch + "," + epoch + "," + src + "," + dst + ",40000," + dpt + ",6,1,64,10.0.0.1,1,2";
        return new NfdumpCsvNetFlowRecordParser().parse(List.of(
                "firstSeen,lastSeen,srcAddr,dstAddr,srcPort,dstPort,proto,packets,bytes,routerIP,input,output",
                line
        )).getFirst();
    }

    private List<NetworkFlow> savePortScan(String sourceIp, String destinationIp, Instant start, int... ports) {
        List<NetworkFlow> flows = new ArrayList<>();
        for (int index = 0; index < ports.length; index++) {
            Instant flowStart = start.plusSeconds(index);
            flows.add(networkFlowRepository.save(new NetworkFlow(
                    null,
                    flowStart,
                    flowStart.plusSeconds(1),
                    1000,
                    sourceIp,
                    destinationIp,
                    40000 + index,
                    ports[index],
                    "TCP",
                    4,
                    256,
                    "test-exporter",
                    1,
                    2,
                    UUID.randomUUID().toString().replace("-", ""),
                    sourceIp + " -> " + destinationIp + ":" + ports[index]
            )));
        }
        return flows;
    }

    private String uniqueHost() {
        return "fw-tz-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Instant tenMinutesAgo() {
        return ZonedDateTime.now(DEPLOYMENT_ZONE)
                .minusMinutes(10)
                .withNano(0)
                .toInstant();
    }
}
