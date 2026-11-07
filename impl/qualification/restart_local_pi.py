"""Restart the isolated Pi process created by run_local_stack.py."""

import json
import os
import signal
import subprocess
import sys
import time
from pathlib import Path

import httpx


def main() -> int:
    state = Path(sys.argv[1]).resolve()
    saved = json.loads((state / "pi-process.json").read_text())
    pid = int((state / "pi.pid").read_text())
    os.killpg(pid, signal.SIGTERM)
    for _ in range(30):
        try:
            with httpx.Client(timeout=1) as client:
                client.get(saved["health"])
        except httpx.HTTPError:
            break
        time.sleep(1)
    with (state / "pi.log").open("ab") as log:
        process = subprocess.Popen(saved["command"], cwd=saved["cwd"],
                                   env=saved["env"], stdout=log,
                                   stderr=subprocess.STDOUT, start_new_session=True)
    (state / "pi.pid").write_text(str(process.pid))
    for _ in range(180):
        if process.poll() is not None:
            raise RuntimeError("restarted Pi service exited; inspect pi.log")
        try:
            with httpx.Client(timeout=2) as client:
                response = client.get(saved["health"], headers={
                    "authorization": f"Bearer {saved['env']['INFERENCE_BROKER_TOKEN']}",
                    "x-awp-owner": saved["env"]["AWP_OWNER_IDENTITY"]})
            if response.status_code == 200:
                return 0
        except httpx.HTTPError:
            pass
        time.sleep(1)
    raise RuntimeError("restarted Pi service did not become ready")


if __name__ == "__main__":
    sys.exit(main())
