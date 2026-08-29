from __future__ import annotations

import unittest

from app.bpf import build_host_filter, filter_argv, validate_interface, validate_ip
from app.captures import CaptureJob, clamp_duration, tcpdump_argv


class BpfTests(unittest.TestCase):
    def test_host_filter_for_port_scan_pair(self) -> None:
        expression = build_host_filter("10.0.0.10", "10.10.10.20")
        self.assertEqual(expression, "host 10.0.0.10 and host 10.10.10.20")
        self.assertEqual(
            filter_argv("10.0.0.10", "10.10.10.20"),
            ["host", "10.0.0.10", "and", "host", "10.10.10.20"],
        )

    def test_invalid_interface_rejected(self) -> None:
        with self.assertRaises(ValueError):
            validate_interface("eth1; rm -rf /", {"eth1"})
        with self.assertRaises(ValueError):
            validate_interface("eth0", {"eth1"})
        with self.assertRaises(ValueError):
            validate_interface("../eth1", {"eth1"})

    def test_invalid_ip_rejected(self) -> None:
        with self.assertRaises(ValueError):
            validate_ip("10.0.0.10; touch /tmp/pwned")
        with self.assertRaises(ValueError):
            validate_ip("not-an-ip")

    def test_duration_limit_enforced(self) -> None:
        self.assertEqual(clamp_duration(120), 60)
        self.assertEqual(clamp_duration(1), 5)
        self.assertEqual(clamp_duration(20), 20)

    def test_tcpdump_argv_has_no_shell_metacharacters_or_raw_command(self) -> None:
        job = CaptureJob(
            id="abc",
            status="PENDING",
            interface="eth1",
            source_ip="10.0.0.10",
            destination_ip="10.10.10.20",
            duration_seconds=20,
            filter_expression="host 10.0.0.10 and host 10.10.10.20",
            file_path=__import__("pathlib").Path("/tmp/captures/abc.pcap"),
        )
        argv = tcpdump_argv(job)
        joined = " ".join(argv)
        self.assertNotIn(";", joined)
        self.assertNotIn("|", joined)
        self.assertNotIn("`", joined)
        self.assertNotIn("$(", joined)
        self.assertIn("tcpdump", argv)
        self.assertIn("eth1", argv)
        self.assertEqual(argv[-5:], ["host", "10.0.0.10", "and", "host", "10.10.10.20"])
        self.assertTrue(argv[0].endswith("timeout") or argv[0] == "timeout")


if __name__ == "__main__":
    unittest.main()
