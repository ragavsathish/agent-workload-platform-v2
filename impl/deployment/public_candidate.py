"""Isolated v2 public candidate controls. External qualification is separate."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import signal
import socket
import subprocess
import sys
import time
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

from deployment import MediaProof, VersionStore
from stack import load_env

MIN_SERVE_VERSION = (1, 104, 4)
PROJECT = re.compile(r"[a-z][a-z0-9_-]{2,63}\Z")
NODE = re.compile(r"[a-z][a-z0-9-]{2,62}\Z")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
ROOT = Path(__file__).resolve().parents[2]
COMPOSE = Path(__file__).resolve().parent / "compose.yml"


def _version(value: str) -> tuple[int, int, int]:
    match = re.match(r"(\d+)\.(\d+)\.(\d+)", value.strip())
    if not match:
        raise ValueError("Tailscale version is unavailable")
    return tuple(map(int, match.groups()))


def _origin(value: str | None) -> bool:
    if not value:
        return False
    parsed = urlsplit(value)
    return (parsed.scheme == "https" and parsed.hostname is not None
            and parsed.hostname.endswith(".ts.net") and parsed.port is None
            and parsed.path in ("", "/") and not parsed.username
            and not parsed.password and not parsed.query and not parsed.fragment)


def _overlap(a: Path, b: Path) -> bool:
    left, right = a.resolve(), b.resolve()
    return left == right or left.is_relative_to(right) or right.is_relative_to(left)


def candidate_identity(project: str, node: str, state: Path, existing_state: Path) -> dict:
    if not PROJECT.fullmatch(project) or project == "pipecat-mlx-voice":
        raise ValueError("candidate project identity is invalid or reused")
    if not NODE.fullmatch(node) or node == "pipecat-edge":
        raise ValueError("candidate node identity is invalid or reused")
    if _overlap(state, existing_state):
        raise ValueError("candidate state overlaps existing state")
    return {"project": project, "node": node, "state": str(state.resolve())}


def preflight(config: dict, *, occupied_ports: set[int],
              occupied_projects: set[str], existing_healthy: bool,
              tailscale_version: str) -> dict:
    reasons: list[str] = []
    project = str(config.get("project", ""))
    if not PROJECT.fullmatch(project) or project in occupied_projects or project == "pipecat-mlx-voice":
        reasons.append("project-collision")
    ports = config.get("ports", [])
    if not isinstance(ports, list) or len(ports) != len(set(ports)) or any(
            not isinstance(port, int) or port < 1024 or port > 65535 for port in ports):
        reasons.append("invalid-ports")
    else:
        reasons.extend(f"port:{port}" for port in ports if port in occupied_ports)
    state = config.get("state")
    existing_state = config.get("existing_state")
    if not state or not Path(state).is_absolute() or (existing_state and _overlap(Path(state), Path(existing_state))):
        reasons.append("state-collision")
    try:
        if _version(tailscale_version) < MIN_SERVE_VERSION:
            reasons.append("tailscale-version")
    except ValueError:
        reasons.append("tailscale-version")
    if not existing_healthy:
        reasons.append("existing-service-unhealthy")
    return {"ready": not reasons, "reasons": sorted(set(reasons))}


def exposure_decision(tailscale_version: str, origin: str | None,
                      owner: str | None, *, authenticated_node: bool = False) -> dict:
    missing: list[str] = []
    try:
        if _version(tailscale_version) < MIN_SERVE_VERSION:
            missing.append("tailscale-version")
    except ValueError:
        missing.append("tailscale-version")
    if not _origin(origin):
        missing.append("candidate-origin")
    if not owner or not owner.strip() or owner.endswith(".test"):
        missing.append("owner-login")
    if not authenticated_node:
        missing.append("authenticated-candidate-node")
    return {"public_ready": not missing, "missing": missing}


def owner_entry(expected: str, actual: str, expected_origin: str,
                actual_origin: str, peer: str, candidate_node: str, *, trusted: bool) -> dict:
    granted = bool(trusted and expected and actual == expected and _origin(expected_origin)
                   and actual_origin == expected_origin and peer and peer != candidate_node)
    return {"granted": granted, "peer": peer if granted else None}


def adversarial_entry(expected: str, actual: str, *, supplied_owner: str = "",
                      local_marker: str = "", service_token: str = "",
                      trusted: bool = True) -> dict:
    # Untrusted claims never enter the identity calculation.
    _ = (supplied_owner, local_marker, service_token)
    return {"granted": bool(trusted and actual and actual == expected)}


def spoken_journey(origin: str, owner_peer: str, candidate_node: str,
                   peer_address: str, selected_address: str, candidate_type: str,
                   input_samples: int, output_samples: int, turn_id: str,
                   conversation_id: str, answer_hash: str, *, private_turn: str,
                   private_conversation: str, private_answer_hash: str) -> dict:
    proof = MediaProof(origin, peer_address, selected_address, candidate_type,
                       input_samples, output_samples, turn_id, answer_hash)
    qualifies = (bool(owner_peer) and owner_peer != candidate_node and proof.qualifies()
                 and bool(conversation_id) and bool(HEX64.fullmatch(answer_hash))
                 and turn_id == private_turn and conversation_id == private_conversation
                 and answer_hash == private_answer_hash)
    return {"qualified": qualifies, "turn_id": turn_id if qualifies else None,
            "conversation_id": conversation_id if qualifies else None}


def failed_media(*, signaling: bool, selected_pair: bool,
                 input_samples: int, output_samples: int) -> dict:
    completed = bool(signaling and selected_pair and input_samples > 0 and output_samples > 0)
    return {"status": "completed" if completed else "unavailable", "completed": completed}


def restart_rehearsal(before: dict, after: dict) -> dict:
    fields = ("turn_id", "task_id", "state_id", "turn_count", "task_count")
    preserved = all(before.get(field) == after.get(field) for field in fields)
    preserved = preserved and before.get("turn_count") == 1 and before.get("task_count") == 1
    return {"preserved": preserved, "state_id": before.get("state_id") if preserved else None}


def rollback_rehearsal(state: Path, qualified: str, broken: str, owner_view: dict) -> dict:
    """Exercise the real VersionStore against disposable candidate release paths."""
    if not owner_view.get("turn_id") or not owner_view.get("task_id") or qualified == broken:
        raise ValueError("rollback rehearsal requires distinct versions and owner identities")
    releases = state / "releases"
    for version in (qualified, broken):
        target = releases / version / "impl/deployment"
        target.mkdir(parents=True, exist_ok=True)
        (target / "stack.py").write_text("# rehearsal\n")
    store = VersionStore(state / "versions.json", state / "current")
    store.register(qualified, releases / qualified)
    store.qualify(qualified)
    store.register(broken, releases / broken)
    store.activate(broken)
    store.rollback()
    return {"active": store.active(), "preserved": store.active() == qualified
            and (state / "current").resolve() == (releases / qualified).resolve(),
            "turn_id": owner_view["turn_id"], "task_id": owner_view["task_id"]}


def paired_denial(*, foreign_denied: bool, owner_passed: bool,
                  candidate_healthy: bool) -> dict:
    return {"qualified": foreign_denied and owner_passed and candidate_healthy}


def release_decision(*, local_ready: bool, owner_login: str | None,
                     origin: str | None, owner_peer: str | None,
                     foreign_peer: str | None, media_passed: bool,
                     v1_healthy: bool, authenticated_node: bool = False,
                     owner_verified: bool = False, origin_verified: bool = False) -> dict:
    missing = []
    if not owner_login: missing.append("owner-login")
    if not _origin(origin): missing.append("candidate-origin")
    if not owner_peer: missing.append("separate-owner-peer")
    if not foreign_peer: missing.append("foreign-peer")
    if not media_passed: missing.append("public-media")
    if not local_ready: missing.append("local-readiness")
    if not v1_healthy: missing.append("existing-service-health")
    if not authenticated_node: missing.append("authenticated-candidate-node")
    if not owner_verified: missing.append("verified-owner-login")
    if not origin_verified: missing.append("verified-candidate-origin")
    qualified = not missing
    return {"status": "public-qualified" if qualified else "prepared-only",
            "public_qualified": qualified, "missing": missing}


def available_port(port: int) -> bool:
    with socket.socket() as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("127.0.0.1", port))
        except OSError:
            return False
    return True


def tailnet_image_version(image: str) -> str:
    if not image.startswith("docker.io/tailscale/tailscale@sha256:"):
        raise ValueError("candidate requires a pinned Tailscale image digest")
    result = subprocess.run(["docker", "run", "--rm", "--network", "none",
                             "--entrypoint", "tailscale", image, "version"],
                            check=True, capture_output=True, text=True, timeout=30)
    return result.stdout.splitlines()[0].strip()


def compose_shape(project: str, node: str, edge_env: Path, broker_port: int,
                  gateway_port: int, gitea_port: int = 9750) -> dict:
    env = {**os.environ, "AWP_PUBLIC_PROJECT": project, "AWP_PUBLIC_NODE": node,
           "AWP_PUBLIC_EDGE_ENV": str(edge_env), "AWP_BROKER_PORT": str(broker_port),
           "AWP_GATEWAY_PORT": str(gateway_port), "AWP_GITEA_PORT": str(gitea_port)}
    result = subprocess.run(["docker", "compose", "--profile", "candidate-dependency",
                             "-f", str(COMPOSE), "config",
                             "--format", "json"], env=env, check=True,
                            capture_output=True, text=True, timeout=30)
    shape = json.loads(result.stdout)
    if shape.get("name") != project:
        raise ValueError("candidate Compose project mismatch")
    services = shape.get("services", {})
    tailscale = services.get("tailscale", {})
    if tailscale.get("hostname") != node or tailscale.get("environment", {}).get("TS_HOSTNAME") != node:
        raise ValueError("candidate tailnet node mismatch")
    for name, service in services.items():
        if name == "gitea":
            ports = service.get("ports") or []
            if len(ports) != 1 or ports[0].get("host_ip") != "127.0.0.1" or str(ports[0].get("published")) != str(gitea_port):
                raise ValueError("candidate Gitea route is not loopback-only")
            continue
        if service.get("ports") or service.get("network_mode") == "host":
            raise ValueError(f"candidate service {name} has a host route")
    for name, volume in shape.get("volumes", {}).items():
        if not str(volume.get("name", "")).startswith(project + "_"):
            raise ValueError(f"candidate volume {name} is not project-scoped")
    return shape


def validate_private_config(config: dict) -> dict:
    """Validate candidate wiring without returning secret values."""
    project = str(config["project"])
    node = str(config["node"])
    state = Path(config["state"]).resolve()
    identity = candidate_identity(project, node, state, Path(config["existing_state"]).resolve())
    service = load_env(Path(config["service_env"]))
    edge = load_env(Path(config["edge_env"]))
    ports = {key: int(config["ports"][key]) for key in ("grain", "pi", "broker", "gateway")}
    expected = {"AWP_GRAIN_SESSION_PORT": "grain", "PI_VOICE_SERVICE_PORT": "pi",
                "INFERENCE_BROKER_PORT": "broker", "AGENT_GATEWAY_PORT": "gateway"}
    for key, port_key in expected.items():
        if service.get(key) != str(ports[port_key]):
            raise ValueError(f"candidate {key} does not match selected port")
    owner = service.get("AWP_OWNER_IDENTITY", "")
    if not owner or owner.endswith(".test") or edge.get("EDGE_ALLOWED_USERS") != owner:
        raise ValueError("candidate exact owner is absent or inconsistent")
    origin = edge.get("PIPECAT_ALLOWED_ORIGINS", "")
    if not _origin(origin) or origin != config.get("origin"):
        raise ValueError("candidate origin is absent or inconsistent")
    if edge.get("AGENT_GATEWAY_BASE_URL") != f"http://host.docker.internal:{ports['gateway']}/v1":
        raise ValueError("candidate edge gateway target mismatch")
    for key in ("INFERENCE_BROKER_TOKEN", "AGENT_GATEWAY_TOKEN"):
        if not service.get(key) or edge.get(key) != service[key]:
            raise ValueError(f"candidate {key} is absent or inconsistent")
    state_keys = ("AWP_TASKING_STORAGE_DIR", "AWP_SESSION_STORAGE_DIR",
                  "PI_VOICE_SESSION_DIR", "AWP_TASK_WORKSPACE_DIR", "AWP_TASK_WORKER_LOG_DIR")
    for key in state_keys:
        value = service.get(key)
        if not value or not Path(value).resolve().is_relative_to(state):
            raise ValueError(f"candidate {key} is outside state")
    if service.get("AWP_GRAIN_SESSION_URL") != f"http://127.0.0.1:{ports['grain']}":
        raise ValueError("candidate Grain URL mismatch")
    if service.get("PI_VOICE_SERVICE_URL") != f"http://127.0.0.1:{ports['pi']}":
        raise ValueError("candidate Pi URL mismatch")
    shape = compose_shape(project, node, Path(config["edge_env"]), ports["broker"], ports["gateway"],
                          int(config.get("gitea_port", 9750)))
    image = shape["services"]["tailscale"]["image"]
    return {**identity, "ports": ports, "origin": origin,
            "owner_sha256": hashlib.sha256(owner.encode()).hexdigest(), "tailscale_image": image}


def native_dependencies(service_env: Path) -> list[str]:
    """Check the startup dependencies the native voice service actually uses."""
    service = load_env(service_env)
    missing: list[str] = []
    assets = service.get("GONDOLIN_BROWSER_ASSETS", "")
    if not assets or not Path(assets).exists():
        missing.append("gondolin-assets")
    wassette = service.get("WASSETTE_BIN", "")
    if not wassette or not Path(wassette).is_file() or not os.access(wassette, os.X_OK):
        missing.append("wassette-binary")
    endpoint = service.get("GITEA_ENDPOINT_URL", "")
    parsed = urlsplit(endpoint)
    if (parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost"}
            or parsed.port in {None, 3001} or not service.get("GITEA_TOKEN")):
        missing.append("isolated-gitea")
    else:
        request = Request(endpoint.rstrip("/") + "/api/v1/user",
                          headers={"Authorization": "token " + service["GITEA_TOKEN"]})
        try:
            with urlopen(request, timeout=3) as response:
                if response.status != 200:
                    missing.append("isolated-gitea")
        except Exception:
            missing.append("isolated-gitea")
        owner = service.get("GITEA_OWNER", "")
        repository = service.get("PI_VOICE_GITEA_REPOSITORY", "agent-workload-platform")
        if not owner or not repository or "/" in owner or "/" in repository:
            missing.append("candidate-repository")
        else:
            repo_request = Request(endpoint.rstrip("/") + f"/api/v1/repos/{owner}/{repository}",
                                   headers={"Authorization": "token " + service["GITEA_TOKEN"]})
            try:
                with urlopen(repo_request, timeout=3) as response:
                    repository_info = json.load(response)
                    if response.status != 200 or repository_info.get("private", True):
                        missing.append("candidate-repository")
            except Exception:
                missing.append("candidate-repository")
    return missing


def _compose_projects() -> set[str]:
    result = subprocess.run(["docker", "compose", "ls", "--format", "json"],
                            capture_output=True, text=True, check=True, timeout=20)
    return {str(item["Name"]) for item in json.loads(result.stdout)}


def _candidate_gitea_matches(project: str, expected_port: int) -> bool:
    result = subprocess.run(["docker", "ps", "-a", "--filter",
                             f"label=com.docker.compose.project={project}", "--format", "{{json .}}"],
                            capture_output=True, text=True, check=True, timeout=20)
    entries = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    if len(entries) != 1 or entries[0].get("Names") != f"{project}-gitea-1":
        return False
    inspect = subprocess.run(["docker", "inspect", entries[0]["ID"]],
                             capture_output=True, text=True, check=True, timeout=20)
    container = json.loads(inspect.stdout)[0]
    bindings = container.get("HostConfig", {}).get("PortBindings", {}).get("3000/tcp", [])
    mounts = container.get("Mounts", [])
    return (container.get("State", {}).get("Running") is True
            and container.get("Config", {}).get("Labels", {}).get("com.docker.compose.service") == "gitea"
            and bindings == [{"HostIp": "127.0.0.1", "HostPort": str(expected_port)}]
            and len(mounts) == 2
            and all(mount.get("Type") == "volume" and str(mount.get("Name", "")).startswith(project + "_")
                    for mount in mounts))


def _v1_snapshot() -> dict:
    result = subprocess.run(["docker", "ps", "--filter", "label=com.docker.compose.project=pipecat-mlx-voice",
                             "--format", "{{json .}}"], capture_output=True,
                            text=True, check=True, timeout=20)
    entries = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    by_name = {entry.get("Names", "").removeprefix("pipecat-mlx-voice-").removesuffix("-1"): entry
               for entry in entries}
    required = {"tailscale", "traefik", "voice-edge"}
    healthy = (required <= by_name.keys()
               and "Up " in by_name["tailscale"].get("Status", "")
               and all("(healthy)" in by_name[name].get("Status", "")
                       for name in ("traefik", "voice-edge")))
    return {"healthy": healthy, "container_ids": sorted(entry["ID"] for entry in entries),
            "essential_present": sorted(required & by_name.keys()),
            "port_bindings": {name: by_name[name].get("Ports", "") for name in sorted(required & by_name.keys())}}


def live_preflight(config: dict) -> dict:
    wiring = validate_private_config(config)
    image_version = tailnet_image_version(wiring["tailscale_image"])
    existing = _v1_snapshot()
    occupied = {port for port in wiring["ports"].values() if not available_port(port)}
    occupied_projects = _compose_projects()
    if wiring["project"] in occupied_projects and _candidate_gitea_matches(
            wiring["project"], int(config.get("gitea_port", 9750))):
        occupied_projects.remove(wiring["project"])
    base = preflight({"project": wiring["project"], "state": wiring["state"],
                      "existing_state": config["existing_state"],
                      "ports": list(wiring["ports"].values())},
                     occupied_ports=occupied, occupied_projects=occupied_projects,
                     existing_healthy=existing["healthy"], tailscale_version=image_version)
    dependencies = native_dependencies(Path(config["service_env"]))
    base["reasons"] = sorted(set(base["reasons"] + dependencies))
    base["ready"] = not base["reasons"]
    return {"candidate": wiring, "preflight": base, "existing_before": existing,
            "tailscale_version": image_version,
            "public_gate": release_decision(local_ready=False,
                owner_login=None, origin=wiring["origin"], owner_peer=None,
                foreign_peer=None, media_passed=False, v1_healthy=existing["healthy"])}


def _write_report(path: Path, report: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    tmp.chmod(0o600)
    tmp.replace(path)


def run_native(config: dict, report_path: Path) -> None:
    initial = live_preflight(config)
    if not initial["preflight"]["ready"]:
        _write_report(report_path, initial)
        raise RuntimeError("candidate preflight failed")
    command = [sys.executable, str(Path(__file__).resolve().parent / "stack.py"), "run",
               "--env", str(Path(config["service_env"]).resolve()),
               "--state", str(Path(config["state"]).resolve())]
    process = subprocess.Popen(command, cwd=ROOT / "impl", start_new_session=True)
    def stop_on_signal(_signum, _frame):
        raise KeyboardInterrupt
    previous_term = signal.signal(signal.SIGTERM, stop_on_signal)
    try:
        check = [sys.executable, str(Path(__file__).resolve().parent / "stack.py"),
                 "check-ready", "--env", str(Path(config["service_env"]).resolve()),
                 "--state", str(Path(config["state"]).resolve())]
        ready = False
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline and process.poll() is None:
            result = subprocess.run(check, capture_output=True, text=True, timeout=30)
            if result.returncode == 0:
                ready = True
                break
            time.sleep(2)
        initial["native_ready_initial"] = ready
        initial["supervisor_pid"] = process.pid
        initial["existing_during"] = _v1_snapshot()
        _write_report(report_path, initial)
        if not ready or process.poll() is not None or initial["existing_during"] != initial["existing_before"]:
            raise RuntimeError("candidate or existing service changed during native start")
        try:
            process.wait()
        except KeyboardInterrupt:
            initial["stopped_by_operator"] = True
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=5)
        initial["existing_after"] = _v1_snapshot()
        initial["candidate_ports_released"] = all(
            available_port(port) for port in initial["candidate"]["ports"].values())
        _write_report(report_path, initial)
        signal.signal(signal.SIGTERM, previous_term)
    if initial["existing_after"] != initial["existing_before"]:
        raise RuntimeError("existing service changed during candidate shutdown")
    if not initial["candidate_ports_released"]:
        raise RuntimeError("candidate port remained occupied after shutdown")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("preflight", "run-native"))
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    config = json.loads(args.config.read_text())
    if args.command == "preflight":
        report = live_preflight(config)
        _write_report(args.report, report)
        print(json.dumps({"preflight": report["preflight"],
                          "public_gate": report["public_gate"]}, indent=2))
        if not report["preflight"]["ready"]:
            raise SystemExit(1)
    else:
        run_native(config, args.report)


if __name__ == "__main__":
    main()
