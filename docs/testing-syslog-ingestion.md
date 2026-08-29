# Testing Syslog Ingestion

The Spring Boot backend now starts a UDP syslog listener on port `5514` by default.

Default properties:

- `app.syslog.enabled=true`
- `app.syslog.udp-port=5514`
- `app.syslog.default-timezone=Africa/Tunis` (`APP_SYSLOG_DEFAULT_TIMEZONE`)

RFC 3164 timestamps such as `Aug 18 16:16:00` have no year or timezone. The backend
interprets them in `app.syslog.default-timezone`, stores UTC Instants, and returns
ISO-8601 timestamps with a `Z` offset. The frontend converts those values to local time.

Lab senders must emit that same zone. Alpine `date` defaults to UTC; if the parser
is `Africa/Tunis` (UTC+1), a UTC stamp is stored one hour early and Syslog+NetFlow
correlation against unix-epoch NetFlow fails the 30-minute window. `forward-syslog.sh`
prints RFC 3164 in `Africa/Tunis` (POSIX `CET-1` without tzdata). Override with
`SYSLOG_TIMESTAMP_TZ` if `APP_SYSLOG_DEFAULT_TIMEZONE` changes. NetFlow v5 `unix_secs`
is already UTC and must not be shifted.

Send test messages locally with `nc`:

```bash
echo "<134>firewall-01 DENY TCP 192.168.1.50:53000 -> 10.0.0.5:22" | nc -u -w1 127.0.0.1 5514
echo "<134>switch-01 interface Gi0/1 link down" | nc -u -w1 127.0.0.1 5514
echo "<134>linux-server sshd[123]: Failed password for invalid user admin from 192.168.1.77 port 54231 ssh2" | nc -u -w1 127.0.0.1 5514
echo "<134>ups-01 battery low 12 percent" | nc -u -w1 127.0.0.1 5514
echo "<134>camera-01 RTSP stream lost" | nc -u -w1 127.0.0.1 5514
echo "<134>firewall-01 port scan detected source_ip=198.51.100.22 ports=22|80|443|8443" | nc -u -w1 127.0.0.1 5514
```

What to verify in AIOps Sentinel:

1. New entries appear in `Events & Logs`
2. High-severity syslog events appear in `Alerts`
3. Related incidents are created or updated in `Incidents`
4. Event detail dialogs show:
   - `SYSLOG` badge
   - source IP
   - syslog source name if matched
   - parser profile
   - raw log

If you configure a `Syslog Source` whose `Expected host or sender IP` matches the sender IP or the parsed hostname, the collector will reuse that device mapping when creating the event.
