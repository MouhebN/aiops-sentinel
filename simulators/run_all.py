from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

from common import DEFAULT_BACKEND_URL


SIMULATORS = [
    "server_simulator.py",
    "camera_simulator.py",
    "ups_simulator.py",
    "firewall_simulator.py",
    "switch_simulator.py",
    "application_simulator.py",
]


def main() -> None:
    parser = argparse.ArgumentParser(description="Run all AIOps device simulators.")
    parser.add_argument("--backend-url", default=DEFAULT_BACKEND_URL)
    parser.add_argument("--interval", type=float, default=5.0)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    simulator_dir = Path(__file__).parent
    processes = []

    try:
        for simulator in SIMULATORS:
            command = [
                sys.executable,
                str(simulator_dir / simulator),
                "--backend-url",
                args.backend_url,
                "--interval",
                str(args.interval),
            ]
            if args.dry_run:
                command.append("--dry-run")
            processes.append(subprocess.Popen(command))

        for process in processes:
            process.wait()
    except KeyboardInterrupt:
        print("\nStopping simulators...")
        for process in processes:
            process.terminate()
        for process in processes:
            process.wait(timeout=5)


if __name__ == "__main__":
    main()
