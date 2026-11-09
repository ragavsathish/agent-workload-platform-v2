#!/usr/bin/env python3
"""Return a redacted, read-only inventory of reviewed v2 host services and k3d readiness."""
from __future__ import annotations

import json
import subprocess

SERVICES = (
    "com.sathish.pipecat-broker",
    "com.sathish.pipecat-pi-durable-owner",
    "com.sathish.pipecat-pi-voice",
    "com.sathish.pipecat-music-generation",
    "com.sathish.pipecat-digital-twin-listener",
    "com.sathish.pipecat-host-observability",
    "com.sathish.pipecat-stack-alert-monitor",
)


def run(argv: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(argv, text=True, capture_output=True, check=False, timeout=15)


def main() -> None:
    launchctl = run(["/bin/launchctl", "list"])
    if launchctl.returncode:
        raise SystemExit("launchctl inventory failed")
    loaded_labels = {line.split()[-1] for line in launchctl.stdout.splitlines() if line.split()}

    api = run(["/usr/local/bin/kubectl", "--context", "k3d-awp-v2-ops", "get", "--raw=/readyz"])
    if api.returncode or api.stdout.strip() != "ok":
        raise SystemExit("local k3d API readiness probe failed")
    pods = run(["/usr/local/bin/kubectl", "--context", "k3d-awp-v2-ops", "-n", "awp-v2",
                "get", "pods", "-o", "json"])
    if pods.returncode:
        raise SystemExit("v2 pod readiness inventory failed")
    pod_values = json.loads(pods.stdout).get("items", [])
    expected = {"grain-0", "agent-gateway-", "gitea-0", "s3mock-0", "oxigraph-0", "otel-lgtm-0"}
    pod_ready = {}
    for item in pod_values:
        name = item.get("metadata", {}).get("name", "")
        key = next((wanted for wanted in expected if name == wanted or (wanted.endswith("-") and name.startswith(wanted))), None)
        if key:
            statuses = item.get("status", {}).get("containerStatuses", [])
            pod_ready[key.rstrip("-")] = bool(statuses) and all(status.get("ready") for status in statuses)
    print(json.dumps({
        "host_services": {label: ("loaded" if label in loaded_labels else "not_loaded") for label in SERVICES},
        "k3d_api_ready": True,
        "v2_pods_ready": pod_ready,
    }, sort_keys=True))


if __name__ == "__main__":
    main()
