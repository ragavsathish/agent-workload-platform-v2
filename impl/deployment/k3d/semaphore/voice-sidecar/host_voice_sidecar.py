#!/usr/bin/env python3
"""Run a bounded, secret-redacted Semaphore operation on the v2 voice sidecar."""
from __future__ import annotations

import argparse
import base64
import hashlib
import importlib.util
import json
import os
import re
import subprocess
import sys
import socket
import time
from pathlib import Path
from urllib.request import Request, urlopen


def load_stack(runtime_root: Path, expected_sha: str):
    stack_path = runtime_root / "impl/deployment/stack.py"
    if not stack_path.is_file() or hashlib.sha256(stack_path.read_bytes()).hexdigest() != expected_sha:
        raise RuntimeError("reviewed runtime stack digest mismatch")
    sys.path.insert(0, str(stack_path.parent))
    spec = importlib.util.spec_from_file_location("awp_voice_sidecar_stack", stack_path)
    if spec is None or spec.loader is None:
        raise RuntimeError("reviewed runtime stack could not be loaded")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module, stack_path


def check_http(url: str, headers: dict[str, str] | None = None) -> bool:
    try:
        request = Request(url, headers=headers or {})
        with urlopen(request, timeout=5) as response:
            return response.status == 200
    except Exception:
        return False


def active_broker_environment(preferred_port: str) -> dict[str, str]:
    ports = tuple(dict.fromkeys((preferred_port, "9543")))
    for port in ports:
        try:
            pids = subprocess.run(
                ["lsof", "-nP", "-t", f"-iTCP:{port}", "-sTCP:LISTEN"],
                capture_output=True, text=True, timeout=5, check=True,
            ).stdout.split()
        except Exception:
            continue
        for pid in pids:
            try:
                record = subprocess.run(
                    ["ps", "eww", "-p", pid], capture_output=True, text=True,
                    timeout=5, check=True,
                ).stdout
            except Exception:
                continue
            if "voice_agent.speech.presentation.http" not in record:
                continue
            matches = list(re.finditer(r"(?<!\S)([A-Z][A-Z0-9_]*)=", record))
            environment = {
                match.group(1): record[match.end():matches[index + 1].start()].strip()
                if index + 1 < len(matches) else record[match.end():].strip()
                for index, match in enumerate(matches)
            }
            if environment.get("INFERENCE_BROKER_PORT") != port:
                continue
            required = (
                "AWP_OWNER_IDENTITY", "INFERENCE_BROKER_TOKEN", "GITEA_OWNER",
                "GITEA_TOKEN", "GRAFANA_MCP_SERVER_TOKEN", "GONDOLIN_BROWSER_ASSETS",
                "ARTIFACT_BUCKET", "ARTIFACT_ACCESS_KEY", "ARTIFACT_SECRET_KEY",
                "WASSETTE_BIN", "WASSETTE_COMPONENT_DIR", "WASSETTE_ENV_FILE",
            )
            if all(environment.get(key) for key in required):
                return environment
    raise RuntimeError("no active v2 broker process has a complete host runtime environment")


def read_cluster_secrets() -> dict[str, dict[str, str]]:
    result = subprocess.run(
        ["kubectl", "--context", "k3d-awp-v2-ops", "-n", "awp-v2",
         "get", "secret", "broker-client", "grain-session", "-o", "json"],
        capture_output=True, text=True, timeout=10, check=True,
    )
    secrets_payload = json.loads(result.stdout)
    by_name = {item["metadata"]["name"]: item.get("data", {})
               for item in secrets_payload.get("items", [])}
    optional = subprocess.run(
        ["kubectl", "--context", "k3d-awp-v2-ops", "-n", "awp-v2",
         "get", "secret", "gitea-client", "-o", "json"],
        capture_output=True, text=True, timeout=10,
    )
    if optional.returncode == 0:
        item = json.loads(optional.stdout)
        by_name[item["metadata"]["name"]] = item.get("data", {})
    return by_name


def hydrate_cluster_credentials(secrets_payload: dict[str, dict[str, str]]) -> None:
    mapping = {
        "INFERENCE_BROKER_TOKEN": ("broker-client", "token"),
        "AWP_GRAIN_SESSION_TOKEN": ("grain-session", "token"),
        "AWP_OWNER_IDENTITY": ("grain-session", "owner"),
    }
    for variable, (secret_name, key) in mapping.items():
        encoded = secrets_payload.get(secret_name, {}).get(key)
        if not encoded:
            raise RuntimeError("required existing cluster credential is unavailable")
        os.environ[variable] = base64.b64decode(encoded, validate=True).decode("utf-8")


def run_gitea_cli(*args: str) -> str:
    result = subprocess.run(
        ["kubectl", "--context", "k3d-awp-v2-ops", "-n", "awp-v2", "exec",
         "gitea-0", "--", "/usr/local/bin/gitea", *args],
        capture_output=True, text=True, timeout=30,
    )
    if result.returncode != 0:
        raise RuntimeError("Gitea owner bootstrap failed")
    return result.stdout.strip()


def gitea_http(base: str, path: str, token: str, *, payload: dict | None = None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = Request(
        f"{base}{path}", data=body,
        headers={"Authorization": f"token {token}", "Content-Type": "application/json"},
        method="GET" if payload is None else "POST",
    )
    with urlopen(request, timeout=8) as response:
        content = response.read(65_536)
        return response.status, (json.loads(content) if content else {})


def gitea_forward_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def ensure_gitea_workspace(secrets_payload: dict[str, dict[str, str]]) -> None:
    owner = os.environ.get("GITEA_OWNER", "")
    repository = os.environ.get("PI_VOICE_GITEA_REPOSITORY", "agent-workload-platform")
    email = os.environ.get("AWP_OWNER_IDENTITY", "")
    if not owner or not email:
        raise RuntimeError("Gitea workspace identity is not configured")
    encoded = secrets_payload.get("gitea-client", {}).get("token")
    if encoded:
        token = base64.b64decode(encoded, validate=True).decode("utf-8")
    else:
        users = run_gitea_cli("admin", "user", "list")
        if not any(line.split()[1:2] == [owner] for line in users.splitlines()[1:]):
            run_gitea_cli(
                "admin", "user", "create", "--username", owner, "--email", email,
                "--random-password", "--must-change-password=false",
            )
        token = run_gitea_cli(
            "admin", "user", "generate-access-token", "--username", owner,
            "--token-name", "awp-v2-voice-" + os.urandom(6).hex(),
            "--scopes", "read:user,read:repository,write:repository,write:user", "--raw",
        )
        if len(token) < 32 or any(char.isspace() for char in token):
            raise RuntimeError("Gitea did not return a usable owner credential")
        manifest = {
            "apiVersion": "v1", "kind": "Secret",
            "metadata": {"name": "gitea-client", "namespace": "awp-v2"},
            "type": "Opaque",
            "data": {"token": base64.b64encode(token.encode()).decode(),
                     "owner": base64.b64encode(owner.encode()).decode()},
        }
        applied = subprocess.run(
            ["kubectl", "--context", "k3d-awp-v2-ops", "-n", "awp-v2", "apply", "-f", "-"],
            input=json.dumps(manifest), text=True, capture_output=True, timeout=15,
        )
        if applied.returncode != 0:
            raise RuntimeError("Gitea owner credential could not be stored in Kubernetes")

    os.environ["GITEA_OWNER"] = owner
    os.environ["GITEA_TOKEN"] = token
    port = gitea_forward_port()
    command = ["kubectl", "--context", "k3d-awp-v2-ops", "-n", "awp-v2",
               "port-forward", "--address", "127.0.0.1", "service/gitea", f"{port}:3000"]
    forward = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                               stdin=subprocess.DEVNULL, start_new_session=True)
    base = f"http://127.0.0.1:{port}"
    try:
        for _ in range(40):
            if forward.poll() is not None:
                raise RuntimeError("Gitea workspace forward stopped unexpectedly")
            try:
                with urlopen(base + "/api/healthz", timeout=2) as response:
                    if response.status == 200:
                        break
            except Exception:
                time.sleep(0.25)
        else:
            raise RuntimeError("Gitea workspace service did not become ready")
        status, identity = gitea_http(base, "/api/v1/user", token)
        if status != 200 or identity.get("login") != owner:
            raise RuntimeError("Gitea owner credential does not match its account")
        repo_path = f"/api/v1/repos/{owner}/{repository}"
        try:
            gitea_http(base, repo_path, token)
        except Exception as exc:
            if getattr(exc, "code", None) != 404:
                raise RuntimeError("Gitea workspace repository could not be inspected") from None
            gitea_http(base, "/api/v1/user/repos", token, payload={
                "name": repository,
                "description": "Private Pi voice workspace",
                "private": True,
                "auto_init": True,
                "default_branch": "main",
            })
    finally:
        forward.terminate()
        try:
            forward.wait(timeout=5)
        except subprocess.TimeoutExpired:
            forward.kill()
            forward.wait(timeout=2)


def legacy_health() -> dict[str, bool]:
    token = os.environ.get("INFERENCE_BROKER_TOKEN", "")
    owner = os.environ.get("AWP_OWNER_IDENTITY", "")
    if not token or not owner:
        return {"legacy_broker": False, "legacy_pi": False}
    auth = {"Authorization": f"Bearer {token}"}
    return {
        "legacy_broker": check_http("http://127.0.0.1:9443/health", auth),
        "legacy_pi": check_http("http://127.0.0.1:9446/health", {
            **auth, "X-AWP-Owner": owner,
        }),
    }


def run_check_ready(stack_path: Path, env_file: Path, state: Path,
                    environment: dict[str, str]) -> dict[str, bool]:
    command = [sys.executable, str(stack_path), "check-ready", "--env", str(env_file),
               "--state", str(state)]
    deadline = time.monotonic() + 75
    last: dict[str, bool] = {"check_ready_command": False}
    while True:
        try:
            result = subprocess.run(command, cwd=stack_path.parents[1], env=environment,
                                    capture_output=True, text=True, timeout=20)
            payload = json.loads(result.stdout)
        except (OSError, subprocess.SubprocessError, ValueError, json.JSONDecodeError):
            payload = None
            result = None
        if not isinstance(payload, dict) or not all(isinstance(value, bool) for value in payload.values()):
            last = {"check_ready_shape": False}
        else:
            last = {**payload, "check_ready_command": result is not None and result.returncode == 0}
            if all(last.values()):
                return last
        if time.monotonic() >= deadline:
            return last
        time.sleep(2)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("preflight", "start", "check-ready", "stop", "status"))
    parser.add_argument("--runtime-root", type=Path, required=True)
    parser.add_argument("--stack-sha256", required=True)
    parser.add_argument("--env", type=Path, required=True)
    parser.add_argument("--state", type=Path, required=True)
    args = parser.parse_args()
    runtime_root = args.runtime_root.resolve()
    env_file = args.env.resolve()
    state = args.state.resolve()
    if env_file.name != "voice-sidecar.env" or env_file.parent.name != "voice-sidecar":
        raise SystemExit("refusing a non-sidecar environment path")
    if state.name != "state" or state.parent.name != "voice-sidecar":
        raise SystemExit("refusing a non-sidecar state path")
    if runtime_root.name != "current" and not runtime_root.name.endswith("-full"):
        raise SystemExit("runtime root must be the reviewed staged v2 runtime")
    stack, stack_path = load_stack(runtime_root, args.stack_sha256)
    overlay = stack.load_env(env_file)
    if overlay.get("AWP_NATIVE_PROFILE") != "voice-sidecar":
        raise SystemExit("service overlay is not the v2 voice-sidecar profile")
    if args.action == "stop":
        result = stack.stop_stack(state, env_file)
        print(json.dumps({"stopped": bool(result.get("stopped")),
                          "already_stopped": bool(result.get("already_stopped")),
                          "services": result.get("services", [])}, sort_keys=True))
        return
    if args.action == "status":
        statuses = stack.service_status(state)
        if not statuses or not all(item.get("running") for item in statuses.values()):
            print(json.dumps({"services_running": False, "readiness": {}}, sort_keys=True))
            return
    os.environ.update(active_broker_environment(overlay.get("INFERENCE_BROKER_PORT", "9553")))
    secrets_payload = read_cluster_secrets()
    hydrate_cluster_credentials(secrets_payload)
    if args.action in {"preflight", "start"}:
        ensure_gitea_workspace(secrets_payload)
    environment = stack.service_environment(env_file, state)
    specs = stack.native_specs(environment)
    expected = {spec.name for spec in specs}
    if not expected or "grain" in expected or "gateway" in expected:
        raise SystemExit("sidecar selection includes an owner service")
    if environment.get("INFERENCE_BROKER_PORT") == "9443" or environment.get("PI_VOICE_SERVICE_PORT") == "9446":
        raise SystemExit("sidecar ports overlap legacy listeners")

    legacy = legacy_health() if args.action in {"preflight", "start"} else {}
    if args.action in {"preflight", "start"} and not all(legacy.values()):
        print(json.dumps({"preflight": "blocked", **legacy}, sort_keys=True))
        raise SystemExit(1)

    if args.action == "preflight":
        print(json.dumps({"preflight": "passed", **legacy,
                          "sidecar_services": sorted(expected)}, sort_keys=True))
        return
    if args.action == "start":
        existing = stack.service_status(state)
        already_running = bool(existing) and all(item.get("running") for item in existing.values())
        try:
            started = {"started": False} if already_running else stack.start_stack(state, env_file)
        except Exception:
            try:
                stack.stop_stack(state, env_file)
            except Exception:
                pass
            print(json.dumps({"started": False, "rolled_back_sidecar": True,
                              "legacy": legacy}, sort_keys=True))
            raise SystemExit(1) from None
        checks = run_check_ready(stack_path, env_file, state, environment)
        legacy_after = legacy_health()
        if not all(checks.values()) or not all(legacy_after.values()):
            if not already_running:
                stack.stop_stack(state, env_file)
            print(json.dumps({"started": False, "rolled_back_sidecar": True,
                              "already_running": already_running,
                              "readiness": checks, "legacy": legacy_after}, sort_keys=True))
            raise SystemExit(1)
        print(json.dumps({"started": bool(started.get("started")),
                          "already_running": already_running,
                          "readiness": checks, "legacy": legacy_after,
                          "sidecar_services": sorted(expected)}, sort_keys=True))
        return
    if args.action == "check-ready":
        checks = run_check_ready(stack_path, env_file, state, environment)
        print(json.dumps({"readiness": checks}, sort_keys=True))
        if not all(checks.values()):
            raise SystemExit(1)
        return
    statuses = stack.service_status(state)
    checks = run_check_ready(stack_path, env_file, state, environment) if statuses else {}
    print(json.dumps({"services_running": bool(statuses) and all(
        item.get("running") for item in statuses.values()),
        "readiness": checks}, sort_keys=True))


if __name__ == "__main__":
    main()
