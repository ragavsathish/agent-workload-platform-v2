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
import time
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


def execution_mode_for_level(level: str) -> str:
    if level == "durable":
        return "host"
    if level == "full":
        return "gondolin"
    if level == "voice":
        raise ValueError("durable task execution is unavailable at voice level")
    raise ValueError("AWP_DEPLOYMENT_LEVEL must be voice, durable, or full")


def native_specs(env: dict[str, str]) -> tuple[ServiceSpec, ...]:
    level = env.get("AWP_DEPLOYMENT_LEVEL", "full")
    if level not in {"voice", "durable", "full"}:
        raise ValueError("AWP_DEPLOYMENT_LEVEL must be voice, durable, or full")
    require(env, "AWP_TASKING_STORAGE_DIR", "AWP_SESSION_STORAGE_DIR",
            "AWP_GRAIN_SESSION_TOKEN", "AWP_OWNER_IDENTITY",
            "AWP_ELIGIBLE_WORKSPACES", "AWP_WORKSPACE_MAP_JSON",
            "INFERENCE_BROKER_TOKEN", "GONDOLIN_BROWSER_ASSETS")
    song_owner: tuple[ServiceSpec, ...] = ()
    if env.get("AWP_PI_DURABLE_OWNER_URL"):
        require(env, "AWP_PI_DURABLE_OWNER_TOKEN_FILE", "AWP_SONG_LYRIC_OWNER_DB",
                "AWP_PI_DURABLE_OWNER_PORT")
        owner_url = env["AWP_PI_DURABLE_OWNER_URL"].rstrip("/")
        if owner_url != f"http://127.0.0.1:{env['AWP_PI_DURABLE_OWNER_PORT']}":
            raise ValueError("song lyric owner URL must match its loopback port")
        song_owner = (ServiceSpec("song-lyric-owner",
                                  ("node", str(TASK / "dist/song-lyric-owner-server.js")),
                                  RUNTIME, f"{owner_url}/health"),)
    core = (
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
    )
    if level == "voice":
        return tuple(spec for spec in core if spec.name != "song-lyric-owner")
    return song_owner + core + (ServiceSpec("scheduler", (sys.executable, str(Path(__file__).resolve()),
                                                           "schedule", "--env", env["AWP_DEPLOY_ENV"],
                                                           "--state", env["AWP_DEPLOY_STATE"]), IMPL),)


def service_environment(env_file: Path, state: Path) -> dict[str, str]:
    """Build the complete environment shared by a supervisor and its restart gate."""
    values = load_env(env_file)
    level = values.get("AWP_DEPLOYMENT_LEVEL", "full")
    if level not in {"voice", "durable", "full"}:
        raise ValueError("AWP_DEPLOYMENT_LEVEL must be voice, durable, or full")
    values.update({"AWP_DEPLOY_ENV": str(env_file), "AWP_DEPLOY_STATE": str(state)})
    environment = {**os.environ, **values}
    environment["AWP_DEPLOYMENT_LEVEL"] = level
    if level != "voice":
        environment["WORKER_PI_EXECUTION_MODE"] = execution_mode_for_level(level)
    telemetry = RUNTIME / "products/voice-agent/packages/telemetry/src"
    environment["PYTHONPATH"] = os.pathsep.join([str(BROKER / "src"), str(telemetry),
        environment.get("PYTHONPATH", "")])
    return environment


def schedule(env: dict[str, str], state: Path) -> None:
    level = env.get("AWP_DEPLOYMENT_LEVEL", "full")
    if level not in {"durable", "full"}:
        raise ValueError("durable task scheduler is unavailable at voice level")
    env = {**env, "WORKER_PI_EXECUTION_MODE": execution_mode_for_level(level)}
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


def _ps_table() -> dict[int, tuple[int, int, str]]:
    """Return PID -> (parent PID, process group, command) for safe lifecycle checks."""
    output = subprocess.check_output(
        ["ps", "-axo", "pid=,ppid=,pgid=,command="], text=True
    )
    result: dict[int, tuple[int, int, str]] = {}
    for line in output.splitlines():
        fields = line.strip().split(None, 3)
        if len(fields) != 4:
            continue
        try:
            pid, ppid, pgid = map(int, fields[:3])
        except ValueError:
            continue
        result[pid] = (ppid, pgid, fields[3])
    return result


def _matching_run_processes(state: Path, env_file: Path) -> list[int]:
    script = str(Path(__file__).resolve())
    relative_script = str(Path(__file__).resolve().relative_to(ROOT))
    return [pid for pid, (_ppid, pgid, command) in _ps_table().items()
            if pgid == pid and "stack.py run" in command
            and (script in command or relative_script in command)
            and str(env_file) in command and str(state) in command]


def _owned_supervisor(state: Path, env_file: Path) -> tuple[int, dict[str, int]] | None:
    """Find the unique stack.py run parent that owns every recorded child."""
    run_dir = state / "run"
    records = {}
    for file in run_dir.glob("*.json"):
        try:
            record = json.loads(file.read_text())
            records[file.stem] = int(record["pid"])
        except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
            raise RuntimeError(f"invalid service record: {file.name}") from exc
    if not records:
        if _matching_run_processes(state, env_file):
            raise RuntimeError("matching supervisor exists without verified service records")
        return None

    processes = _ps_table()
    parent_ids: set[int] = set()
    for name, pid in records.items():
        process = processes.get(pid)
        if process is None:
            raise RuntimeError(f"recorded {name} process is not running")
        ppid, pgid, _command = process
        if pgid != pid:
            raise RuntimeError(f"recorded {name} process group is unexpected")
        parent_ids.add(ppid)
    if len(parent_ids) != 1:
        raise RuntimeError("recorded services do not share one supervisor")

    supervisor_pid = next(iter(parent_ids))
    supervisor = processes.get(supervisor_pid)
    if supervisor is None:
        raise RuntimeError("AWP v2 supervisor is not running")
    _ppid, pgid, command = supervisor
    script = str(Path(__file__).resolve())
    relative_script = str(Path(__file__).resolve().relative_to(ROOT))
    expected = ((script in command or relative_script in command)
                and "stack.py run" in command and "--env" in command
                and str(env_file) in command and "--state" in command
                and str(state) in command)
    if not expected or pgid != supervisor_pid:
        raise RuntimeError("process owning AWP v2 services does not match the requested stack")
    return supervisor_pid, records


def stop_stack(state: Path, env_file: Path, timeout: float = 45.0) -> dict[str, object]:
    """Gracefully stop only the verified native stack supervisor and its children."""
    if not env_file.is_file():
        raise FileNotFoundError("service environment file does not exist")
    load_env(env_file)
    owned = _owned_supervisor(state, env_file)
    if owned is None:
        return {"stopped": False, "already_stopped": True, "services": []}
    supervisor_pid, children = owned
    started = time.monotonic()
    os.kill(supervisor_pid, signal.SIGTERM)
    deadline = started + timeout
    while time.monotonic() < deadline:
        processes = _ps_table()
        supervisor_alive = supervisor_pid in processes
        live_children = [name for name, pid in children.items() if pid in processes]
        if not supervisor_alive and not live_children:
            (state / "supervisor.json").unlink(missing_ok=True)
            return {"stopped": True, "already_stopped": False,
                    "services": sorted(children),
                    "duration_seconds": round(time.monotonic() - started, 2)}
        time.sleep(0.25)
    raise TimeoutError("native stack did not stop cleanly; no force-kill was attempted")


def start_stack(state: Path, env_file: Path, timeout: float = 180.0) -> dict[str, object]:
    """Start the native stack detached and wait for its supervisor readiness gate."""
    if not env_file.is_file():
        raise FileNotFoundError("service environment file does not exist")
    load_env(env_file)
    if _owned_supervisor(state, env_file) is not None:
        raise RuntimeError("native stack is already running")
    if _matching_run_processes(state, env_file):
        raise RuntimeError("matching native stack process exists without verified service ownership")
    state.mkdir(parents=True, exist_ok=True, mode=0o700)
    state.chmod(0o700)
    log_path = state / "supervisor.log"
    command = [sys.executable, "-u", str(Path(__file__).resolve()), "run",
               "--env", str(env_file), "--state", str(state)]
    with log_path.open("ab", buffering=0) as log:
        process = subprocess.Popen(command, cwd=IMPL, stdin=subprocess.DEVNULL,
                                   stdout=log, stderr=subprocess.STDOUT,
                                   start_new_session=True, close_fds=True)
    (state / "supervisor.json").write_text(json.dumps({"pid": process.pid,
                                                       "command": command}) + "\n")
    (state / "supervisor.json").chmod(0o600)
    expected_names = {spec.name for spec in native_specs(service_environment(env_file, state))}
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        code = process.poll()
        if code is not None:
            (state / "supervisor.json").unlink(missing_ok=True)
            raise RuntimeError(f"native stack exited during startup with code {code}")
        children = service_status(state)
        if expected_names <= children.keys() and all(
                children[name]["running"] for name in expected_names):
            return {"started": True, "supervisor_pid": process.pid,
                    "services": sorted(expected_names)}
        time.sleep(0.25)
    raise TimeoutError("native stack did not become ready before timeout")


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
    parser.add_argument("command", choices=("run", "schedule", "status", "check-ready", "start", "stop", "install", "rollback", "qualify-version", "activate-version"))
    parser.add_argument("--env", type=Path)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--version")
    parser.add_argument("--release", type=Path)
    parser.add_argument("--current-link", type=Path)
    args = parser.parse_args()
    state = args.state.resolve()
    state.mkdir(parents=True, exist_ok=True, mode=0o700)
    state.chmod(0o700)
    if args.command in {"start", "stop"}:
        if not args.env:
            parser.error("--env is required")
        env_file = args.env.resolve()
        result = start_stack(state, env_file) if args.command == "start" else stop_stack(state, env_file)
        print(json.dumps(result, sort_keys=True))
        return
    if args.command == "status":
        import urllib.request
        values = load_env(args.env.resolve()) if args.env else {}
        services = service_status(state)
        result = {"requested_level": values.get("AWP_DEPLOYMENT_LEVEL", "full"),
                  "active_services": services,
                  "readiness": "ready" if services and all(item["running"] for item in services.values()) else "not_ready"}
        if args.env:
            request = urllib.request.Request(
                f"http://127.0.0.1:{values.get('AWP_GRAIN_SESSION_PORT', '9447')}/internal/deployment/status",
                data=b"{}", method="POST",
                headers={"Authorization": f"Bearer {values.get('AWP_GRAIN_SESSION_TOKEN', '')}",
                         "X-AWP-Owner": values.get("AWP_OWNER_IDENTITY", ""),
                         "Content-Type": "application/json"})
            try:
                with urllib.request.urlopen(request, timeout=3) as response:
                    result["grain_deployment"] = json.load(response)
            except Exception:
                result["grain_deployment"] = {"readiness": "unavailable"}
        print(json.dumps(result, indent=2))
        return
    if args.command == "check-ready":
        import urllib.request
        values = load_env(args.env.resolve()) if args.env else {}
        level = values.get("AWP_DEPLOYMENT_LEVEL", "full")
        if level not in {"voice", "durable", "full"}:
            raise ValueError("AWP_DEPLOYMENT_LEVEL must be voice, durable, or full")
        checks = {
            "grain": (f"http://127.0.0.1:{values.get('AWP_GRAIN_SESSION_PORT', '9447')}/internal/task/list",
                      values.get("AWP_GRAIN_SESSION_TOKEN", "")),
            "pi": (f"http://127.0.0.1:{values.get('PI_VOICE_SERVICE_PORT', '9446')}/health",
                   values.get("INFERENCE_BROKER_TOKEN", "")),
            "broker": (f"http://127.0.0.1:{values.get('INFERENCE_BROKER_PORT', '9443')}/health",
                       values.get("INFERENCE_BROKER_TOKEN", "")),
            "gateway": (f"http://127.0.0.1:{values.get('AGENT_GATEWAY_PORT', '9444')}/health", ""),
        }
        if level != "voice" and values.get("AWP_PI_DURABLE_OWNER_URL"):
            checks["song-lyric-owner"] = (values["AWP_PI_DURABLE_OWNER_URL"].rstrip("/") + "/health", "")
        result = {}
        for name, (url, token) in checks.items():
            request = urllib.request.Request(url, data=b"{}" if name == "grain" else None,
                headers={"Authorization": f"Bearer {token}", "X-AWP-Owner": values.get("AWP_OWNER_IDENTITY", "")})
            try:
                with urllib.request.urlopen(request, timeout=3) as response:
                    result[name] = response.status == 200
            except Exception:
                result[name] = False
        if level != "voice":
            result["scheduler"] = bool(service_status(state).get("scheduler", {}).get("running"))
        if result.get("grain"):
            payload = json.dumps({"active-level": level, "services": result,
                                  "readiness": "ready" if all(result.values()) else "degraded"}).encode()
            request = urllib.request.Request(
                f"http://127.0.0.1:{values.get('AWP_GRAIN_SESSION_PORT', '9447')}/internal/deployment/observe",
                data=payload, method="POST",
                headers={"Authorization": f"Bearer {values.get('AWP_GRAIN_SESSION_TOKEN', '')}",
                         "X-AWP-Owner": values.get("AWP_OWNER_IDENTITY", ""),
                         "Content-Type": "application/json"})
            try:
                with urllib.request.urlopen(request, timeout=3):
                    pass
            except Exception:
                result["deployment-state"] = False
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
    load_env(env_file)
    if args.command == "install":
        print(install_launchagent(env_file, state, "com.awp.v2.owner-stack"))
        return
    environment = service_environment(env_file, state)
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
