package com.aiops.backend.syslog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SyslogParserTimestampTests {

    private static final DateTimeFormatter RFC3164 = DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.ENGLISH);
    private static final Instant REAL_EVENT = Instant.parse("2026-08-22T15:33:05Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-22T16:00:00Z"), ZoneOffset.UTC);

    @Test
    void rfc3164TimestampWithoutTimezoneUsesConfiguredDeploymentZone() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T16:03:35Z"), ZoneOffset.UTC);
        SyslogParserService parser = new SyslogParserService("Africa/Tunis", clock);

        ParsedSyslogMessage parsed = parser.parse(
                "<134>Aug 18 16:16:00 BANK-FW-01 DENY TCP 192.168.0.15:45122 -> 192.168.0.1:22",
                SyslogParserProfile.FIREWALL
        );

        assertThat(parsed.occurredAt()).isEqualTo(Instant.parse("2026-08-18T15:16:00Z"));
        assertThat(parsed.occurredAt()).isBeforeOrEqualTo(clock.instant());
    }

    @Test
    void rfc3164TimestampWithoutYearUsesCurrentYear() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T16:03:35Z"), ZoneOffset.UTC);
        SyslogParserService parser = new SyslogParserService("UTC", clock);

        ParsedSyslogMessage parsed = parser.parse(
                "<134>Aug 18 16:16:00 BANK-FW-01 DENY TCP 192.168.0.15:45122 -> 192.168.0.1:22",
                SyslogParserProfile.GENERIC
        );

        assertThat(parsed.occurredAt()).isEqualTo(Instant.parse("2026-08-18T16:16:00Z"));
    }

    @Test
    void futureYearBoundaryTimestampIsAssignedPreviousYear() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T12:00:00Z"), ZoneOffset.UTC);
        SyslogParserService parser = new SyslogParserService("UTC", clock);

        ParsedSyslogMessage parsed = parser.parse(
                "<134>Dec 31 23:00:00 BANK-FW-01 DENY TCP 192.168.0.15:45122 -> 192.168.0.1:22",
                SyslogParserProfile.GENERIC
        );

        assertThat(parsed.occurredAt()).isEqualTo(Instant.parse("2025-12-31T23:00:00Z"));
        assertThat(parsed.occurredAt()).isBeforeOrEqualTo(clock.instant());
    }

    @ParameterizedTest
    @CsvSource({
            "UTC, 2026-08-22T15:33:05Z",
            "Africa/Tunis, 2026-08-22T14:33:05Z",
            "Europe/Paris, 2026-08-22T13:33:05Z",
            "America/New_York, 2026-08-22T19:33:05Z"
    })
    void rfc3164WallClockConvertsUsingConfiguredZoneOnly(String zoneId, String expectedUtc) {
        SyslogParserService parser = new SyslogParserService(zoneId, CLOCK);
        ParsedSyslogMessage parsed = parser.parse(
                "<134>Aug 22 15:33:05 BANK-FW-01 DENY TCP 10.0.0.10:45122 -> 10.10.10.20:22",
                SyslogParserProfile.FIREWALL
        );
        assertThat(parsed.occurredAt()).isEqualTo(Instant.parse(expectedUtc));
    }

    @ParameterizedTest
    @CsvSource({
            "UTC",
            "Africa/Tunis",
            "Europe/Paris",
            "America/New_York"
    })
    void senderStampInParserZoneKeepsTenMinuteGapToUnixNetFlow(String zoneId) {
        SyslogParserService parser = new SyslogParserService(zoneId, CLOCK);
        String stamp = RFC3164.withZone(ZoneId.of(zoneId)).format(REAL_EVENT);
        ParsedSyslogMessage parsed = parser.parse(
                "<134>" + stamp + " BANK-FW-01 DENY TCP 10.0.0.10:45122 -> 10.10.10.20:22",
                SyslogParserProfile.FIREWALL
        );
        Instant netFlowUtc = REAL_EVENT.plus(Duration.ofMinutes(10));

        assertThat(parsed.occurredAt()).isEqualTo(REAL_EVENT);
        assertThat(Duration.between(parsed.occurredAt(), netFlowUtc).toMinutes()).isEqualTo(10);
        assertThat(Duration.between(parsed.occurredAt(), netFlowUtc).abs().toMinutes()).isNotEqualTo(70);
    }

    @Test
    void utcSenderStampParsedAsAfricaTunisIsOneHourEarly() {
        SyslogParserService parser = new SyslogParserService("Africa/Tunis", CLOCK);
        String utcStamp = RFC3164.withZone(ZoneOffset.UTC).format(REAL_EVENT);
        ParsedSyslogMessage parsed = parser.parse(
                "<134>" + utcStamp + " BANK-FW-01 DENY TCP 10.0.0.10:45122 -> 10.10.10.20:22",
                SyslogParserProfile.FIREWALL
        );

        assertThat(utcStamp).isEqualTo("Aug 22 15:33:05");
        assertThat(parsed.occurredAt()).isEqualTo(Instant.parse("2026-08-22T14:33:05Z"));
        assertThat(Duration.between(parsed.occurredAt(), REAL_EVENT).toHours()).isEqualTo(1);
    }
}
