"""Restart the Pi child of an already running local v2 supervisor."""

from __future__ import annotations

import argparse
import json
import os
import signal
import time
import urllib.request
from pathlib import Path


def env_values(path: Path) -> dict[str, str]:
    if path.stat().st_mode & 0o077:
        raise PermissionError("private service environment must be mode 0600")
    values = {}
    for line in path.read_text().splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, _, value = line.partition("=")
            values[key] = value.strip().strip('"')
    return values


def restart(state: Path, env_path: Path) -> tuple[int, int]:
    record = state / "run/pi.json"
    before = int(json.loads(record.read_text())["pid"])
    values = env_values(env_path)
    os.killpg(before, signal.SIGTERM)
    sent_at = time.monotonic()
    forced = False
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        try:
            after = int(json.loads(record.read_text())["pid"])
            if after != before:
                request = urllib.request.Request(
                    f"http://127.0.0.1:{values.get('PI_VOICE_SERVICE_PORT', '9446')}/health",
                    headers={"Authorization": f"Bearer {values['INFERENCE_BROKER_TOKEN']}",
                             "X-AWP-Owner": values['AWP_OWNER_IDENTITY']})
                with urllib.request.urlopen(request, timeout=3) as response:
                    if response.status == 200:
                        return before, after
        except (FileNotFoundError, KeyError, ValueError, OSError):
            pass
        if not forced and time.monotonic() - sent_at > 10:
            try:
                os.killpg(before, signal.SIGKILL)
            except ProcessLookupError:
                pass
            forced = True
        time.sleep(1)
    raise TimeoutError("supervised Pi child did not restart")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--env", type=Path, required=True)
    args = parser.parse_args()
    old, new = restart(args.state, args.env)
    print(f"supervised Pi restart: {old} -> {new}")
