"""Prove one real lyric-task-to-private-audio journey in a fresh supervised v2 state."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import secrets
import signal
import socket
import sqlite3
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import re
from pathlib import Path

from local_release import new_report, record, save_report

ROOT = Path(__file__).resolve().parents[2]
IMPL = ROOT / "impl"
STACK = IMPL / "deployment/stack.py"
sys.path.insert(0, str(IMPL / "deployment"))


def port() -> int:
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def request(url: str, token: str, identity: str, method: str = "GET",
            body: dict | None = None, key: str | None = None, audio: bool = False):
    if method == "GET":
        url += ("&" if "?" in url else "?") + urllib.parse.urlencode({"identity": identity})
    else:
        body = {**(body or {}), "identity": identity}
    headers = {"Authorization": f"Bearer {token}"}
    if key:
        headers["Idempotency-Key"] = key
    if body is not None:
        headers["Content-Type"] = "application/json"
    submitted = urllib.request.Request(url, data=json.dumps(body).encode() if body is not None else None,
                                       headers=headers, method=method)
    try:
        with urllib.request.urlopen(submitted, timeout=120) as response:
            data = response.read()
            return response.status, data if audio else json.loads(data)
    except urllib.error.HTTPError as error:
        data = error.read()
        try: return error.code, json.loads(data)
        except ValueError: return error.code, {}


def await_health(url: str, process: subprocess.Popen, timeout: int = 180) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("supervised stack exited before readiness")
        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                if response.status == 200:
                    return
        except OSError:
            pass
        time.sleep(1)
    raise TimeoutError("supervised gateway did not become ready")


def owner_row(db_path: Path, task_id: str) -> dict:
    with sqlite3.connect(db_path) as db:
        db.row_factory = sqlite3.Row
        row = db.execute("SELECT * FROM lyric_tasks WHERE task_id=?", (task_id,)).fetchone()
    if row is None:
        raise AssertionError("durable owner has no lyric task")
    return dict(row)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-env", required=True, type=Path)
    parser.add_argument("--state-dir", required=True, type=Path)
    args = parser.parse_args()
    state = args.state_dir.resolve()
    if state.exists():
        raise SystemExit("state directory must be fresh")
    state.mkdir(parents=True, mode=0o700)
    for name in ("scratch", "tasking", "sessions", "song", "song-artifacts", "pi-sessions", "task-sockets", "worker-scratch"):
        (state / name).mkdir(mode=0o700)
    from stack import load_env
    values = load_env(args.base_env.resolve())
    values.pop("AWP_INCIDENT_RULES_FILE", None)
    owner = "song-owner@example.test"
    grain_port, pi_port, broker_port, gateway_port, lyric_port = (port() for _ in range(5))
    grain_url = f"http://127.0.0.1:{grain_port}"
    gateway_url = f"http://127.0.0.1:{gateway_port}"
    lyric_url = f"http://127.0.0.1:{lyric_port}"
    token_file = state / "lyric-owner.token"
    token_file.write_text(secrets.token_urlsafe(48))
    token_file.chmod(0o600)
    gateway_token = secrets.token_urlsafe(48)
    gateway_token_file = state / "gateway.token"
    gateway_token_file.write_text(gateway_token)
    gateway_token_file.chmod(0o600)
    values.update({
        "AWP_OWNER_IDENTITY": owner, "ORCHESTRATOR_ALLOWED_USERS": owner,
        "AWP_ELIGIBLE_WORKSPACES": "scratch", "AWP_WORKSPACE_MAP_JSON": json.dumps({"scratch": str(state / "scratch")}),
        "AWP_DEFAULT_WORKSPACE": "scratch", "VOICE_DEFAULT_WORKSPACE": "scratch",
        "AWP_TASKING_STORAGE_DIR": str(state / "tasking"), "AWP_SESSION_STORAGE_DIR": str(state / "sessions"),
        "AWP_KNOWLEDGE_STORAGE_DIR": str(state / "knowledge"),
        "AWP_INCIDENT_STORAGE_DIR": str(state / "incidents"),
        "AWP_SONG_STORAGE_DIR": str(state / "song"), "AWP_SONG_ARTIFACT_DIR": str(state / "song-artifacts"),
        "AWP_TASK_WORKSPACE_DIR": str(state / "task-workspaces"),
        "AWP_TASK_WORKER_LOG_DIR": str(state / "worker-logs"), "WORKER_SCRATCH_ROOT": str(state / "worker-scratch"),
        "PI_SESSION_DIR": str(state / "pi-sessions"), "PI_TASK_SESSION_SOCKET_DIR": str(state / "task-sockets"),
        "PI_VOICE_SESSION_DIR": str(state / "pi-sessions"), "PI_VOICE_CWD": str(state / "scratch"),
        "AWP_GRAIN_SESSION_PORT": str(grain_port), "AWP_GRAIN_SESSION_URL": grain_url,
        "AWP_GRAIN_SESSION_TOKEN": secrets.token_urlsafe(48),
        "PI_VOICE_SERVICE_PORT": str(pi_port), "PI_VOICE_SERVICE_URL": f"http://127.0.0.1:{pi_port}",
        "INFERENCE_BROKER_PORT": str(broker_port), "INFERENCE_BROKER_BASE_URL": f"http://127.0.0.1:{broker_port}/v1",
        "INFERENCE_BROKER_TOKEN": secrets.token_urlsafe(48),
        "AGENT_GATEWAY_PORT": str(gateway_port), "AGENT_GATEWAY_BASE_URL": gateway_url + "/v1",
        "AGENT_GATEWAY_TOKEN": gateway_token, "AGENT_GATEWAY_TOKEN_FILE": str(gateway_token_file),
        "AWP_PI_DURABLE_OWNER_URL": lyric_url, "AWP_PI_DURABLE_OWNER_PORT": str(lyric_port),
        "AWP_PI_DURABLE_OWNER_TOKEN_FILE": str(token_file),
        "AWP_SONG_LYRIC_OWNER_DB": str(state / "lyric-owner.sqlite"),
        "AWP_WEBRTC_LOOPBACK_ONLY": "1", "WORKER_PI_EXECUTION_MODE": "gondolin",
    })
    env_path = state / "service.env"
    env_path.write_text("\n".join(f"{key}={value}" for key, value in sorted(values.items())) + "\n")
    env_path.chmod(0o600)
    log = (state / "supervisor.log").open("ab")
    process = subprocess.Popen([sys.executable, str(STACK), "run", "--env", str(env_path), "--state", str(state)],
                               cwd=IMPL, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        await_health(gateway_url + "/health", process)
        print("supervised owner stack ready", flush=True)
        base = gateway_url + "/v1/music/generations"
        token = values["AGENT_GATEWAY_TOKEN"]
        status, created = request(base, token, owner, "POST", {"brief": "A concise original song about durable work and careful review.",
                                                              "style": "Warm acoustic", "durationSeconds": 30,
                                                              "workspace": "scratch"}, "slice16-lyric-request")
        if status != 200 or not isinstance(created, dict) or not created.get("lyrics-task-id"):
            raise AssertionError(f"owner creation failed: HTTP {status}")
        generation_id, lyric_task_id = created["id"], created["lyrics-task-id"]
        print(f"lyric task accepted: {lyric_task_id}", flush=True)
        status, replay = request(base, token, owner, "POST", {"brief": "A concise original song about durable work and careful review.",
                                                             "style": "Warm acoustic", "durationSeconds": 30,
                                                             "workspace": "scratch"}, "slice16-lyric-request")
        assert status == 200 and replay["id"] == generation_id
        deadline = time.monotonic() + 900
        ready = None
        while time.monotonic() < deadline:
            status, view = request(base + "/" + generation_id, token, owner)
            if status != 200: raise AssertionError("owner read failed")
            if view.get("status") == "lyrics-ready":
                ready = view; break
            if view.get("status") in ("failed", "cancelled"):
                raise AssertionError("lyric task ended without reviewable manifest")
            time.sleep(3)
        if not ready: raise TimeoutError("real Pi lyric task did not complete")
        row = owner_row(state / "lyric-owner.sqlite", lyric_task_id)
        result = json.loads(row["result_json"])
        assert row["state"] == "completed" and row["attempt"] == 1
        assert result["task_id"] == lyric_task_id and result["generation_id"] == generation_id
        assert result["identity"] == owner and result["manifest"]
        assert ready["lyric-result-digest"] and ready["lyric-attempt"] == 1
        assert ready["manifest"] and ready["manifest-digest"] and not ready.get("audio-path")
        print("claimed Pi lyric result validated and presented", flush=True)
        status, refusal = request(base + "/" + generation_id + "/render", token, owner, "POST", {})
        assert status >= 400 and not ready.get("render-task-id"), refusal
        decision_id = str(uuid.uuid4())
        status, approved = request(base + "/" + generation_id + "/approve", token, owner, "POST",
                                   {"decisionId": decision_id, "manifestDigest": ready["manifest-digest"], "choice": "approved"})
        assert status == 200 and approved["approval"] == "approved" and approved["decision-id"] == decision_id
        status, rendering = request(base + "/" + generation_id + "/render", token, owner, "POST", {})
        assert status == 200 and rendering["render-task-id"]
        assert rendering["status"] in ("generating-music", "complete")
        render_task_id = rendering["render-task-id"]
        status, replay_render = request(base + "/" + generation_id + "/render", token, owner, "POST", {})
        assert status == 200 and replay_render["render-task-id"] == render_task_id
        deadline = time.monotonic() + 600
        complete = None
        while time.monotonic() < deadline:
            status, view = request(base + "/" + generation_id, token, owner)
            if status != 200: raise AssertionError("owner song read failed")
            if view.get("status") == "complete": complete = view; break
            if view.get("status") in ("failed", "cancelled"): raise AssertionError("render failed")
            time.sleep(3)
        if not complete: raise TimeoutError("ACE-Step render did not complete")
        status, audio = request(base + "/" + generation_id + "/audio", token, owner, audio=True)
        assert status == 200 and len(audio) > 10_000
        foreign_status, _ = request(base + "/" + generation_id + "/audio", token, "foreign@example.test", audio=True)
        assert foreign_status == 403
        audio_hash = hashlib.sha256(audio).hexdigest()
        (state / "song-audio.m4a").write_bytes(audio)
        media = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries",
            "format=duration,size:stream=codec_name,sample_rate,channels", "-of", "json",
            str(state / "song-audio.m4a")], text=True))
        duration = float(media["format"]["duration"])
        assert 25 <= duration <= 35 and int(media["format"]["size"]) == len(audio)
        assert any(stream.get("codec_name") == "aac" for stream in media["streams"])
        volume = subprocess.run(["ffmpeg", "-hide_banner", "-i", str(state / "song-audio.m4a"),
                                 "-af", "volumedetect", "-f", "null", "-"],
                                capture_output=True, text=True, check=True).stderr
        match = re.search(r"mean_volume: (-?[0-9.]+) dB", volume)
        assert match and float(match.group(1)) > -50
        report = new_report(version="slice16-candidate", source_revision=subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
            state_identity=state.name, owner=owner)
        record(report, "song", "exercised", evidence={
            "generation_id": generation_id, "lyric_task_id": lyric_task_id,
            "lyric_attempt": row["attempt"], "lyric_result_digest": ready["lyric-result-digest"],
            "manifest_digest": ready["manifest-digest"], "decision_id": decision_id,
            "render_task_id": render_task_id, "audio_sha256": audio_hash, "audio_bytes": len(audio),
            "audio_duration_seconds": duration, "audio_mean_db": float(match.group(1)),
            "owner_playback_status": status, "foreign_audio_status": foreign_status})
        report["limitations"] = ["Other local journeys are not exercised by this focused qualification.",
                                 "Musical quality and lyric intelligibility need listening judgment.",
                                 "External tailnet playback remains a separate gate."]
        save_report(state / "report.json", report)
        print(f"durable-song-003 and durable-song-009 exercised; report={state / 'report.json'} audio_sha256={audio_hash}", flush=True)
        return 0
    finally:
        try: os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError: pass
        try: process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=10)
        log.close()


if __name__ == "__main__":
    sys.exit(main())
