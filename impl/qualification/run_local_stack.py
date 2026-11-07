"""Run an isolated v2 Grain/Pi/broker/edge stack on loopback for media probes."""

from __future__ import annotations

import argparse
import json
import os
import secrets
import signal
import subprocess
import sys
import time
from pathlib import Path

import httpx
from dotenv import dotenv_values


ROOT = Path(__file__).resolve().parents[2]
IMPL = ROOT / "impl"
RUNTIME = IMPL / "runtime"
EDGE = RUNTIME / "products/voice-agent/apps/edge"
BROKER = RUNTIME / "products/voice-agent/apps/broker"
PI = RUNTIME / "products/voice-agent/apps/pi-agent"


def ready(url: str, headers: dict[str, str] | None = None) -> bool:
    try:
        with httpx.Client(timeout=2) as client:
            return client.get(url, headers=headers).status_code == 200
    except httpx.HTTPError:
        return False


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host-env", required=True, type=Path,
                        help="private dotenv with host model, VM, and service capabilities")
    parser.add_argument("--state-dir", required=True, type=Path)
    parser.add_argument("--owner")
    parser.add_argument("--grain-port", type=int, default=9547)
    parser.add_argument("--pi-port", type=int, default=9546)
    parser.add_argument("--broker-port", type=int, default=9543)
    parser.add_argument("--edge-port", type=int, default=7861)
    parser.add_argument("--test", help="one unittest method, for focused diagnosis")
    args = parser.parse_args()
    state = args.state_dir.resolve()
    state.mkdir(parents=True, exist_ok=True, mode=0o700)
    for name in ("tasking", "sessions", "pi-sessions", "workspace", "voice-diagnostics"):
        (state / name).mkdir(exist_ok=True, mode=0o700)
    host = {key: value for key, value in dotenv_values(args.host_env).items()
            if value is not None}
    owner = args.owner or host.get("JOURNAL_REVIEW_OWNER_IDENTITY")
    if not owner:
        raise SystemExit("--owner or JOURNAL_REVIEW_OWNER_IDENTITY is required")
    if not host.get("GONDOLIN_BROWSER_ASSETS"):
        raise SystemExit("host env needs GONDOLIN_BROWSER_ASSETS")
    if not Path(host["GONDOLIN_BROWSER_ASSETS"]).exists():
        raise SystemExit("configured Gondolin assets do not exist")
    broker_token = secrets.token_urlsafe(40)
    grain_token = secrets.token_urlsafe(40)
    origin = f"http://127.0.0.1:{args.edge_port}"
    common = {**os.environ, **host,
              "AWP_OWNER_IDENTITY": owner,
              "ORCHESTRATOR_ALLOWED_USERS": owner,
              "INFERENCE_BROKER_TOKEN": broker_token,
              "AWP_GRAIN_SESSION_TOKEN": grain_token,
              "AWP_GRAIN_SESSION_URL": f"http://127.0.0.1:{args.grain_port}",
              "AWP_GRAIN_SESSION_PORT": str(args.grain_port),
              "AWP_TASKING_STORAGE_DIR": str(state / "tasking"),
              "AWP_SESSION_STORAGE_DIR": str(state / "sessions"),
              "PI_VOICE_SERVICE_HOST": "127.0.0.1",
              "PI_VOICE_SERVICE_PORT": str(args.pi_port),
              "PI_VOICE_SERVICE_URL": f"http://127.0.0.1:{args.pi_port}",
              "PI_VOICE_SESSION_DIR": str(state / "pi-sessions"),
              "PI_VOICE_CWD": str(state / "workspace"),
              "INFERENCE_BROKER_PORT": str(args.broker_port),
              "INFERENCE_BROKER_BASE_URL": f"http://127.0.0.1:{args.broker_port}/v1",
              "VOICE_LLM_BACKEND": "pi",
              "VOICE_DIAGNOSTIC_DIR": str(state / "voice-diagnostics"),
              "AWP_WEBRTC_LOOPBACK_ONLY": "1",
              "VOICE_DEFAULT_WORKSPACE": host.get("VOICE_DEFAULT_WORKSPACE", "agent-workload-platform"),
              "EDGE_ALLOWED_USERS": owner,
              "PIPECAT_ALLOWED_ORIGINS": origin}
    if common.get("AGENT_GATEWAY_TOKEN_FILE") and not common.get("AGENT_GATEWAY_TOKEN"):
        common["AGENT_GATEWAY_TOKEN"] = Path(common["AGENT_GATEWAY_TOKEN_FILE"]).read_text().strip()
    common["PYTHONPATH"] = str(IMPL / "qualification") + ":" + str(BROKER / "src") + ":" + str(RUNTIME / "products/voice-agent/packages/telemetry/src")
    commands = [
        ("grain", ["clojure", "-J--add-opens=java.base/java.nio=ALL-UNNAMED",
                   "-J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
                   "-M", "-m", "awp-v2.live.main"], IMPL),
        ("pi", ["node", str(PI / "dist/voice-agent-service.js")], RUNTIME),
        ("broker", [str(BROKER / ".venv/bin/python"), "-m",
                    "voice_agent.speech.presentation.http"], BROKER),
        ("edge", [str(EDGE / ".venv/bin/python"), "bot.py", "-t", "webrtc",
                  "--host", "127.0.0.1", "--port", str(args.edge_port)], EDGE),
    ]
    processes: list[subprocess.Popen] = []
    logs = []
    checks = [
        (f"http://127.0.0.1:{args.grain_port}/internal/session/catalog", {}, False),
        (f"http://127.0.0.1:{args.pi_port}/health",
         {"authorization": f"Bearer {broker_token}", "x-awp-owner": owner}, True),
        (f"http://127.0.0.1:{args.broker_port}/health",
         {"authorization": f"Bearer {broker_token}"}, True),
        (f"http://127.0.0.1:{args.edge_port}/api/features", {}, True),
    ]
    try:
        for (name, command, cwd), (url, headers, expect_200) in zip(commands, checks, strict=True):
            log = (state / f"{name}.log").open("ab")
            logs.append(log)
            env = dict(common)
            if name == "edge":
                env["PYTHONPATH"] = str(IMPL / "qualification") + ":" + str(EDGE) + ":" + str(RUNTIME / "products/voice-agent/packages/telemetry/src")
            process = subprocess.Popen(command, cwd=cwd, env=env, stdout=log,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            processes.append(process)
            (state / f"{name}.pid").write_text(str(process.pid))
            if name == "pi":
                path = state / "pi-process.json"
                path.write_text(json.dumps({"command": command, "cwd": str(cwd),
                                            "env": env, "health": url}))
                path.chmod(0o600)
            for _ in range(180):
                if process.poll() is not None:
                    raise RuntimeError(f"{name} exited; inspect {state / (name + '.log')}")
                if expect_200 and ready(url, headers):
                    break
                if not expect_200:
                    try:
                        with httpx.Client(timeout=2) as client:
                            response = client.post(url, headers={
                                "authorization": f"Bearer {grain_token}",
                                "x-awp-owner": owner,
                                "content-type": "application/json"}, json={})
                        if response.status_code in (200, 404):
                            break
                    except httpx.HTTPError:
                        pass
                time.sleep(1)
            else:
                raise RuntimeError(f"{name} did not become ready; inspect {state / (name + '.log')}")
            print(f"{name} ready", flush=True)
        print("isolated v2 stack ready; set qualification variables from this process environment", flush=True)
        print(f"VOICE_EDGE_URL={origin}", flush=True)
        print(f"VOICE_GRAIN_URL=http://127.0.0.1:{args.grain_port}", flush=True)
        print(f"VOICE_BROKER_URL=http://127.0.0.1:{args.broker_port}", flush=True)
        with httpx.Client(timeout=10) as client:
            route_check = client.get(
                f"http://127.0.0.1:{args.broker_port}/v1/pi/sessions",
                headers={"authorization": f"Bearer {broker_token}"},
            )
        if route_check.status_code != 200:
            raise RuntimeError(
                f"broker to Pi session route failed: HTTP {route_check.status_code}, "
                f"{route_check.text[:200]}"
            )
        # Tokens stay in memory; run the qualification command as a child.
        qualification = dict(common)
        qualification.update({"VOICE_EDGE_URL": origin,
                              "VOICE_GRAIN_URL": f"http://127.0.0.1:{args.grain_port}",
                              "VOICE_GRAIN_TOKEN": grain_token,
                              "VOICE_ORIGIN": origin,
                              "VOICE_OWNER_IDENTITY": owner,
                              "VOICE_BROKER_URL": f"http://127.0.0.1:{args.broker_port}",
                              "VOICE_BROKER_TOKEN": broker_token,
                              "VOICE_UTTERANCE": "Describe this selected conversation briefly."})
        qualification["VOICE_RESTART_ARGV_JSON"] = json.dumps([
            str(EDGE / ".venv/bin/python"), str(IMPL / "qualification/restart_local_pi.py"),
            str(state)])
        command = ([str(EDGE / ".venv/bin/python"), "-m", "unittest", args.test, "-v"]
                   if args.test else [str(ROOT / "harness/qualify-voice.sh")])
        result = subprocess.run(command, cwd=IMPL / "qualification", env=qualification)
        return result.returncode
    finally:
        for name, _command, _cwd in reversed(commands[:len(processes)]):
            try:
                os.killpg(int((state / f"{name}.pid").read_text()), signal.SIGTERM)
            except (ProcessLookupError, FileNotFoundError):
                pass
        for process in reversed(processes):
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
        for log in logs:
            log.close()


if __name__ == "__main__":
    sys.exit(main())
