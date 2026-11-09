#!/usr/bin/env python3
"""Read-only, redacted health and Grain turn-status probe for the v2 broker."""
from __future__ import annotations

import json
import os
import subprocess
import urllib.error
import urllib.request

LABEL = "com.sathish.pipecat-broker"
ENV_FILE = "/Users/sathish.narayanan/Library/Application Support/PipecatEdge/apps/broker/broker.env"
KUBECTL = "/usr/local/bin/kubectl"
CONTEXT = "k3d-awp-v2-ops"
NAMESPACE = "awp-v2"


def launch_agent_loaded() -> bool:
    result = subprocess.run(["/bin/launchctl", "list"], capture_output=True, text=True, timeout=15)
    if result.returncode:
        raise RuntimeError("launchctl inventory unavailable")
    return any(line.split() and line.split()[-1] == LABEL for line in result.stdout.splitlines())


def get_json(url: str, token: str) -> dict:
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(request, timeout=10) as response:
        if response.status != 200:
            raise RuntimeError("broker status request failed")
        body = response.read(16_385)
    if len(body) > 16_384:
        raise RuntimeError("broker status response exceeded bound")
    value = json.loads(body)
    if not isinstance(value, dict):
        raise RuntimeError("broker status response shape invalid")
    return value


def edge_health() -> bool:
    # The script and credentials remain inside the pod; only a boolean result is returned.
    script = (
        "import os,urllib.request; "
        "base=os.environ['INFERENCE_BROKER_BASE_URL'].rstrip('/'); "
        "base=base[:-3] if base.endswith('/v1') else base; "
        "req=urllib.request.Request(base+'/health',headers={'Authorization':'Bearer '+os.environ['INFERENCE_BROKER_TOKEN']}); "
        "r=urllib.request.urlopen(req,timeout=8); "
        "print('ok' if r.status==200 else 'unhealthy')"
    )
    result = subprocess.run(
        [KUBECTL, "--context", CONTEXT, "-n", NAMESPACE, "exec", "deployment/voice-edge", "--",
         "/app/.venv/bin/python", "-c", script],
        capture_output=True, text=True, timeout=20,
    )
    return result.returncode == 0 and result.stdout.strip() == "ok"


def main() -> None:
    token = os.getenv("INFERENCE_BROKER_TOKEN", "")
    if len(token) < 32:
        raise SystemExit("broker credential unavailable")
    loaded = launch_agent_loaded()
    local_health = False
    typed_status = "unavailable"
    compaction_status = "unavailable"
    try:
        local_health = get_json("http://127.0.0.1:9443/health", token).get("status") == "ok"
        latest = get_json("http://127.0.0.1:9443/v1/pi/turn/latest", token)
        status = latest.get("status")
        if status in {"none", "accepted", "completed", "failed", "interrupted", "uncertain"}:
            typed_status = status
        context = get_json("http://127.0.0.1:9443/v1/pi/context", token)
        value = context.get("compactionStatus")
        if value in {"idle", "queued", "pending", "completed", "uncertain"}:
            compaction_status = value
    except (OSError, ValueError, RuntimeError, urllib.error.URLError):
        pass
    report = {
        "target": LABEL,
        "loaded": loaded,
        "local_authenticated_health": "ok" if local_health else "unavailable",
        "edge_authenticated_health": "ok" if edge_health() else "unavailable",
        "latest_typed_turn_status": typed_status,
        "compaction_status": compaction_status,
        "native_voice_activity": "not_observable",
        "restart_safe": False,
    }
    print(json.dumps(report, sort_keys=True))


if __name__ == "__main__":
    main()
