"""Supervise native v2 services and schedule Grain-owned tasks.

Install this foreground program as a KeepAlive LaunchAgent on the macOS host.
Public edge deployment is separate: compose.yml plus Tailscale Serve.
"""
from __future__ import annotations

import argparse
import json
import os
import plistlib
import signal
import subprocess
import sys
from pathlib import Path

from deployment import (GrainTaskClient, ScheduledExecutor, ServiceSpec,
                        ServiceSupervisor, TaskScheduler, VersionStore)

ROOT = Path(__file__).resolve().parents[2]
IMPL = ROOT / "impl"
RUNTIME = IMPL / "runtime"
PI = RUNTIME / "products/voice-agent/apps/pi-agent"
BROKER = RUNTIME / "products/voice-agent/apps/broker"
TASK = RUNTIME / "platform/agent-execution/pi-durable-task"


def load_env(path: Path) -> dict[str, str]:
    if path.stat().st_mode & 0o077:
        raise PermissionError("service environment must be mode 0600")
    values: dict[str, str] = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition("=")
        if not sep or not key.isidentifier():
            raise ValueError("invalid environment line")
        values[key] = value.strip().strip('"')
    return values


def require(env: dict[str, str], *names: str) -> None:
    missing = [name for name in names if not env.get(name)]
    if missing:
        raise ValueError("missing service configuration: " + ", ".join(missing))


def native_specs(env: dict[str, str]) -> tuple[ServiceSpec, ...]:
    require(env, "AWP_TASKING_STORAGE_DIR", "AWP_SESSION_STORAGE_DIR",
            "AWP_GRAIN_SESSION_TOKEN", "AWP_OWNER_IDENTITY",
            "AWP_ELIGIBLE_WORKSPACES", "AWP_WORKSPACE_MAP_JSON",
            "INFERENCE_BROKER_TOKEN", "GONDOLIN_BROWSER_ASSETS")
    return (
        ServiceSpec("grain", ("clojure", "-J--add-opens=java.base/java.nio=ALL-UNNAMED",
                              "-J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
                              "-M", "-m", "awp-v2.live.main"), IMPL,
                    f"http://127.0.0.1:{env.get('AWP_GRAIN_SESSION_PORT', '9447')}/internal/task/list",
                    {"Authorization": f"Bearer {env['AWP_GRAIN_SESSION_TOKEN']}"}, "POST"),
        ServiceSpec("pi", ("node", str(PI / "dist/voice-agent-service.js")), RUNTIME,
                    f"http://127.0.0.1:{env.get('PI_VOICE_SERVICE_PORT', '9446')}/health",
                    {"Authorization": f"Bearer {env['INFERENCE_BROKER_TOKEN']}",
                     "X-AWP-Owner": env["AWP_OWNER_IDENTITY"]}),
        ServiceSpec("gateway", (sys.executable, str(Path(__file__).resolve().parent / "gateway.py")),
                    IMPL, f"http://127.0.0.1:{env.get('AGENT_GATEWAY_PORT', '9444')}/health"),
        ServiceSpec("broker", (str(BROKER / ".venv/bin/python"), "-m",
                               "voice_agent.speech.presentation.http"), BROKER,
                    f"http://127.0.0.1:{env.get('INFERENCE_BROKER_PORT', '9443')}/health",
                    {"Authorization": f"Bearer {env['INFERENCE_BROKER_TOKEN']}"}),
        ServiceSpec("scheduler", (sys.executable, str(Path(__file__).resolve()),
                                  "schedule", "--env", env["AWP_DEPLOY_ENV"],
                                  "--state", env["AWP_DEPLOY_STATE"]), IMPL),
    )


def schedule(env: dict[str, str], state: Path) -> None:
    client = GrainTaskClient(env.get("AWP_GRAIN_SESSION_URL", "http://127.0.0.1:9447"),
                             env["AWP_GRAIN_SESSION_TOKEN"])
    worker = ("node", str(TASK / "dist/scheduled-worker.js"))
    executor = ScheduledExecutor(client, TaskScheduler(state / "claims.json"), worker, env,
                                 capacity=int(env.get("AWP_WORKER_CAPACITY", "1")))
    executor.run()


def service_status(state: Path) -> dict[str, dict[str, object]]:
    result = {}
    for file in (state / "run").glob("*.json"):
        record = json.loads(file.read_text())
        pid = int(record["pid"])
        try:
            os.kill(pid, 0)
            running = True
        except ProcessLookupError:
            running = False
        result[file.stem] = {"pid": pid, "running": running}
    return result


def install_launchagent(env_file: Path, state: Path, label: str) -> Path:
    plist = Path.home() / "Library" / "LaunchAgents" / f"{label}.plist"
    plist.parent.mkdir(parents=True, exist_ok=True)
    contents = {
        "Label": label,
        "ProgramArguments": [sys.executable, str(Path(sys.argv[0]).absolute()), "run",
                             "--env", str(env_file), "--state", str(state)],
        "WorkingDirectory": str(IMPL),
        "RunAtLoad": True,
        "KeepAlive": True,
        "ThrottleInterval": 10,
        "StandardOutPath": str(state / "supervisor.log"),
        "StandardErrorPath": str(state / "supervisor.log"),
    }
    plist.write_bytes(plistlib.dumps(contents))
    return plist


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("run", "schedule", "status", "check-ready", "install", "rollback", "qualify-version", "activate-version"))
    parser.add_argument("--env", type=Path)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--version")
    parser.add_argument("--release", type=Path)
    parser.add_argument("--current-link", type=Path)
    args = parser.parse_args()
    state = args.state.resolve()
    state.mkdir(parents=True, exist_ok=True, mode=0o700)
    state.chmod(0o700)
    if args.command == "status":
        print(json.dumps(service_status(state), indent=2))
        return
    if args.command == "check-ready":
        import urllib.request
        values = load_env(args.env.resolve()) if args.env else {}
        checks = {
            "grain": (f"http://127.0.0.1:{values.get('AWP_GRAIN_SESSION_PORT', '9447')}/internal/task/list",
                      values.get("AWP_GRAIN_SESSION_TOKEN", "")),
            "pi": (f"http://127.0.0.1:{values.get('PI_VOICE_SERVICE_PORT', '9446')}/health",
                   values.get("INFERENCE_BROKER_TOKEN", "")),
            "broker": (f"http://127.0.0.1:{values.get('INFERENCE_BROKER_PORT', '9443')}/health",
                       values.get("INFERENCE_BROKER_TOKEN", "")),
            "gateway": (f"http://127.0.0.1:{values.get('AGENT_GATEWAY_PORT', '9444')}/health", ""),
        }
        result = {}
        for name, (url, token) in checks.items():
            request = urllib.request.Request(url, data=b"{}" if name == "grain" else None,
                headers={"Authorization": f"Bearer {token}", "X-AWP-Owner": values.get("AWP_OWNER_IDENTITY", "")})
            try:
                with urllib.request.urlopen(request, timeout=3) as response:
                    result[name] = response.status == 200
            except Exception:
                result[name] = False
        print(json.dumps(result, indent=2))
        if not all(result.values()):
            raise SystemExit(1)
        return
    if args.command in ("rollback", "qualify-version", "activate-version"):
        if not args.current_link:
            parser.error("release operation requires --current-link")
        versions = VersionStore(state / "versions.json", args.current_link.absolute())
        if args.command == "rollback":
            versions.rollback()
        else:
            if not args.version:
                parser.error("--version is required")
            if args.release:
                versions.register(args.version, args.release)
            if args.command == "qualify-version":
                versions.qualify(args.version)
            else:
                versions.activate(args.version)
        print(json.dumps({"active": versions.active(), "current": str(args.current_link),
                          "restart_required": True}))
        return
    if not args.env:
        parser.error("--env is required")
    env_file = args.env.resolve()
    values = load_env(env_file)
    values.update({"AWP_DEPLOY_ENV": str(env_file), "AWP_DEPLOY_STATE": str(state)})
    if args.command == "install":
        print(install_launchagent(env_file, state, "com.awp.v2.owner-stack"))
        return
    environment = {**os.environ, **values}
    telemetry = RUNTIME / "products/voice-agent/packages/telemetry/src"
    environment["PYTHONPATH"] = os.pathsep.join([str(BROKER / "src"), str(telemetry),
        environment.get("PYTHONPATH", "")])
    if args.command == "schedule":
        def stop_schedule(_signum, _frame):
            raise KeyboardInterrupt
        signal.signal(signal.SIGTERM, stop_schedule)
        try:
            schedule(environment, state)
        except KeyboardInterrupt:
            pass
    else:
        supervisor = ServiceSupervisor(native_specs(environment), state / "run", environment)
        def stop(_signum, _frame):
            raise KeyboardInterrupt
        signal.signal(signal.SIGTERM, stop)
        try:
            supervisor.supervise()
        except KeyboardInterrupt:
            supervisor.stop()


if __name__ == "__main__":
    main()
