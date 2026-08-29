# 2026-08-23 — SNMP replies from management subnet

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | Containerlab snmpd (fw / rtr / sw) |
| Type | Bugfix |
| Status | Done |

## Context

BANK-FW-01, CORE-RTR-01, and CORE-SW-01 run Alpine net-snmp from the shared bind-mount `containerlab/services/snmp/` (`/opt/snmp`). Sentinel (`pfe-backend-1`, `172.30.30.3`) polls SNMPv2c community `banklab` on UDP 161.

## Problem

Remote GETs from Sentinel reached snmpd (tcpdump on the node). Local `snmpget -c banklab` worked. No SNMP response was sent. Same on all three nodes.

## Design

Keep `agentAddress udp:0.0.0.0:161`. Do not use `rocommunity banklab` with no SOURCE (world or localhost, depending on net-snmp). Allow read-only `banklab` only from loopback and `172.30.30.0/24`.

`start-snmpd.sh` copies the template to `/etc/snmp/snmpd.conf` only when it actually starts snmpd. Containerlab PID 1 often does not reap, so a dead snmpd stays zombie and `pgrep -x snmpd` skipped the start. Ignore zombies when deciding if snmpd is live.

## Implementation (files)

| File | Role |
| --- | --- |
| `containerlab/services/snmp/snmpd.conf` | `rocommunity banklab 127.0.0.1` and `rocommunity banklab 172.30.30.0/24` |
| `containerlab/services/snmp/start-snmpd.sh` | Treat only non-zombie snmpd as running; recopy template; `snmpd -C -c …` |

## Tests

| Test | Result |
| --- | --- |
| Live `/etc/snmp/snmpd.conf` on fw/rtr/sw has both `rocommunity` lines | Pass |
| `netstat` UDP `0.0.0.0:161` after start | Pass |
| SNMPv2c GET from backend netns source `172.30.30.3` to `.10` `.11` `.12` | Pass (replies) |
| Host `snmpget -v2c -c banklab` to the three mgmt IPs | Pass (`sysDescr`) |
| Local `snmpget` `127.0.0.1` on CORE-SW-01 | Pass |

## Result / example

```text
src 172.30.30.3
OK 172.30.30.10 reply_from=172.30.30.10:161
OK 172.30.30.11 reply_from=172.30.30.11:161
OK 172.30.30.12 reply_from=172.30.30.12:161
SNMPv2-MIB::sysDescr.0 = STRING: BANK-FW-01 Containerlab node (Alpine net-snmp)
SNMPv2-MIB::sysDescr.0 = STRING: CORE-RTR-01 Containerlab node (Alpine net-snmp)
SNMPv2-MIB::sysDescr.0 = STRING: CORE-SW-01 Containerlab node (Alpine net-snmp)
```

## Out of scope

- Topology, mgmt IPs, Sentinel SNMP client, monitoring methods

## Verify commands

```bash
docker exec clab-bank-lab-core-sw-01 grep '^rocommunity' /etc/snmp/snmpd.conf
snmpget -t 3 -r 1 -v2c -c banklab 172.30.30.12 1.3.6.1.2.1.1.1.0
```

Backend netns (source `172.30.30.3`): share `pfe-backend-1` network stack and issue SNMPv2c GET to `172.30.30.10`, `.11`, `.12`.

## Rapport talking points

- Shared snmpd template, not per-node conf
- VACM SOURCE for `banklab` is mgmt `/24`, not `0.0.0.0/0`
- Bind-mount updates are not live until snmpd is started against the copied `/etc/snmp/snmpd.conf`
- sleep(1) as PID 1 leaves snmpd zombies; start script must not treat them as a running agent
