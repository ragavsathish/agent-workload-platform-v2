#!/usr/bin/env python3
"""Read-only, redacted preflight for a native-to-k3d Grain snapshot."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import signal
import sqlite3
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
EXPECTED_NATIVE_SCRIPT = Path(
    "/Users/sathish.narayanan/Documents/git/agent-workload-platform-v2/impl/deployment/stack.py"
)
TRACE_PATH = Path("/private/tmp/awp-v2-preflight.trace")


def _phase(name: str) -> None:
    with TRACE_PATH.open("a", encoding="utf-8") as trace:
        trace.write(f"pid={os.getpid()} elapsed={time.monotonic():.3f} phase={name}\n")
    TRACE_PATH.chmod(0o600)


def _query(base: str, token: str, owner: str, path: str) -> dict:
    _phase(f"query-start:{path}")
    request = urllib.request.Request(
        base.rstrip("/") + path,
        data=b"{}",
        headers={"Authorization": f"Bearer {token}", "X-AWP-Owner": owner,
                 "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        result = json.load(response)
    _phase(f"query-done:{path}")
    return result


def _database_report(path: Path) -> dict[str, object]:
    _phase(f"sqlite-start:{path.name}")
    if not path.is_file():
        return {"exists": False}
    conn = sqlite3.connect(f"file:{path}?mode=ro", uri=True, timeout=5)
    try:
        conn.execute("PRAGMA query_only=ON")
        integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
        tables = {row[0] for row in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'")}
        events = conn.execute("SELECT COUNT(*) FROM events").fetchone()[0] if "events" in tables else 0
        event_tags = conn.execute("SELECT COUNT(*) FROM event_tags").fetchone()[0] if "event_tags" in tables else 0
        tenant_ids = ([row[0] for row in conn.execute("SELECT id FROM tenants ORDER BY id")]
                      if "tenants" in tables else [])
        event_ids_hash = ""
        if "events" in tables:
            digest = hashlib.sha256()
            for tenant_id, event_id in conn.execute(
                    "SELECT tenant_id, id FROM events ORDER BY tenant_id, id"):
                digest.update(tenant_id.encode()); digest.update(b"\0")
                digest.update(event_id.encode()); digest.update(b"\n")
            event_ids_hash = digest.hexdigest()
        tenant_hashes = sorted(hashlib.sha256(x.encode()).hexdigest()[:16] for x in tenant_ids)
        report = {"exists": True, "integrity": integrity, "events": events,
                "event_tags": event_tags, "tenants": len(tenant_ids),
                "tenant_hashes": tenant_hashes, "event_ids_sha256": event_ids_hash,
                "size_bytes": path.stat().st_size}
        _phase(f"sqlite-done:{path.name}")
        return report
    finally:
        conn.close()


def _paths(env: dict[str, str], state: Path) -> dict[str, Path]:
    sessions = Path(env["AWP_SESSION_STORAGE_DIR"]).resolve()
    tasking = Path(env["AWP_TASKING_STORAGE_DIR"]).resolve()
    result = {
        "sessions": sessions,
        "deployment": Path(env.get("AWP_DEPLOYMENT_STORAGE_DIR", str(sessions) + "-deployment")).resolve(),
        "knowledge": Path(env.get("AWP_KNOWLEDGE_STORAGE_DIR", str(sessions) + "-knowledge")).resolve(),
        "song": Path(env.get("AWP_SONG_STORAGE_DIR", str(sessions) + "-song")).resolve(),
        "incidents": Path(env.get("AWP_INCIDENT_STORAGE_DIR", str(sessions) + "-incidents")).resolve(),
        "tasking": tasking,
        "task-workspaces": Path(env.get("AWP_TASK_WORKSPACE_DIR", str(tasking / "workspaces"))).resolve(),
        "song-artifacts": Path(env.get("AWP_SONG_ARTIFACT_DIR", str(sessions) + "-songs")).resolve(),
    }
    for name, path in result.items():
        if not path.is_relative_to(state):
            raise RuntimeError(f"{name} path escapes the configured native state root")
    return result


def _load_stack(stack_path: Path):
    sys.path.insert(0, str(stack_path.parent))
    spec = importlib.util.spec_from_file_location("awp_stack", stack_path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def _owned_supervisor_at(stack, state: Path, env_path: Path,
                         native_script: Path, staged_script_arg: Path | None = None,
                         staged_stack_sha256: str | None = None) -> tuple[int, dict[str, int]] | None:
    run_dir = state / "run"
    records = {}
    for file in run_dir.glob("*.json"):
        record = json.loads(file.read_text())
        records[file.stem] = int(record["pid"])
    if not records:
        return None
    processes = stack._ps_table()
    parent_ids = set()
    for service, pid in records.items():
        proc = processes.get(pid)
        if not proc:
            raise RuntimeError(f"recorded {service} process is not running")
        ppid, pgid, _command = proc
        if pgid != pid:
            raise RuntimeError(f"recorded {service} process group is unexpected")
        parent_ids.add(ppid)
    if len(parent_ids) != 1:
        raise RuntimeError("recorded services do not share one supervisor")
    supervisor_pid = next(iter(parent_ids))
    supervisor = processes.get(supervisor_pid)
    if not supervisor:
        raise RuntimeError("native v2 supervisor is not running")
    _ppid, pgid, command = supervisor
    cwd_probe = subprocess.run(
        ["/usr/sbin/lsof", "-a", "-p", str(supervisor_pid), "-d", "cwd", "-Fn"],
        capture_output=True, text=True, timeout=5, check=False,
    )
    cwd_lines = set(cwd_probe.stdout.splitlines())
    staged_script = Path(stack.__file__).resolve()
    staged_root = Path.home() / "Library/Application Support/AWPv2/k3d/semaphore"
    expected_staged_root = (staged_root / f"source-{staged_stack_sha256}-full"
                            if staged_stack_sha256 else None)
    if (staged_script_arg is not None and staged_script.resolve() != staged_script_arg.resolve()):
        raise RuntimeError("imported native stack differs from the staged runtime path")
    if (expected_staged_root is None
            or staged_script != expected_staged_root / "impl/deployment/stack.py"
            or not staged_script.is_file()
            or hashlib.sha256(staged_script.read_bytes()).hexdigest() != staged_stack_sha256):
        raise RuntimeError("staged native stack path or digest is outside the reviewed runtime pin")
    native_invocation = f"{native_script} run --env {env_path} --state {state}"
    staged_invocation = f"{staged_script} run --env {env_path} --state {state}"
    if native_invocation in command:
        expected_cwd = native_script.parents[1]
        exact_invocation = True
    elif staged_invocation in command:
        expected_cwd = Path(stack.IMPL).resolve()
        exact_invocation = True
    else:
        expected_cwd = None
        exact_invocation = False
    cwd_matches = expected_cwd is not None and f"n{expected_cwd}" in cwd_lines
    if pgid != supervisor_pid or not cwd_matches or not exact_invocation:
        raise RuntimeError("process owning native v2 services does not match the reviewed native stack")
    return supervisor_pid, records


def preflight(env_path: Path, state_path: Path, runtime_root: Path,
              native_script: Path, stack_sha256: str) -> dict[str, object]:
    _phase("start")
    env_path = env_path.resolve()
    _phase("env-path-resolved")
    state = state_path.resolve()
    _phase("state-path-resolved")
    runtime_root = runtime_root.resolve()
    native_script = native_script.resolve()
    if native_script != EXPECTED_NATIVE_SCRIPT:
        raise RuntimeError("native supervisor script path is outside the reviewed source")
    _phase("runtime-path-resolved")
    stack_path = runtime_root / "impl/deployment/stack.py"
    _phase("stack-digest-read-start")
    actual_stack_sha256 = hashlib.sha256(stack_path.read_bytes()).hexdigest()
    _phase("stack-digest-read-done")
    if actual_stack_sha256 != stack_sha256:
        raise RuntimeError("pinned stack lifecycle code hash does not match")
    _phase("stack-import-start")
    stack = _load_stack(stack_path)
    _phase("stack-import-done")
    env = stack.load_env(env_path)
    _phase("service-env-loaded")
    if state != Path("/private/tmp/awp-v2-state"):
        raise RuntimeError("migration preflight is scoped to the reviewed native v2 state path")
    owned = _owned_supervisor_at(stack, state, env_path, native_script,
                                  staged_script_arg=stack_path, staged_stack_sha256=stack_sha256)
    if not owned:
        raise RuntimeError("the exact native v2 supervisor is not running")
    supervisor_pid, services = owned
    _phase("supervisor-verified")
    base = env.get("AWP_GRAIN_SESSION_URL", f"http://127.0.0.1:{env.get('AWP_GRAIN_SESSION_PORT', '9747')}")
    owner = env["AWP_OWNER_IDENTITY"]
    token = env["AWP_GRAIN_SESSION_TOKEN"]

    with urllib.request.urlopen(base.rstrip("/") + "/health", timeout=5) as health_response:
        health_status = health_response.status
    if health_status != 200:
        raise RuntimeError("native Grain health endpoint did not return HTTP 200")

    task_reply = _query(base, token, owner, "/internal/task/list")
    tasks = task_reply.get("tasks", []) if isinstance(task_reply, dict) else task_reply
    if not isinstance(tasks, list):
        raise RuntimeError("owner task query returned an unexpected shape")
    task_statuses = sorted({str(item.get("status", "unknown")) for item in tasks
                            if isinstance(item, dict)})

    catalog = _query(base, token, owner, "/internal/session/catalog")
    sessions = catalog.get("sessions", []) if isinstance(catalog, dict) else []
    latest = _query(base, token, owner, "/internal/session/latest-typed-turn")
    context = _query(base, token, owner, "/internal/session/context")
    routing = _query(base, token, owner, "/internal/session/routing")
    if not isinstance(sessions, list) or not isinstance(latest, dict) or not isinstance(context, dict):
        raise RuntimeError("owner session queries returned an unexpected shape")
    if tasks or any(s in {"pending", "running", "sleeping"} for s in task_statuses):
        raise RuntimeError("owner has active tasks; migration preflight refuses to quiesce")
    if latest.get("status") not in ("none", None):
        raise RuntimeError("owner has an active or unresolved typed turn")
    if context.get("compactionStatus", "idle") != "idle":
        raise RuntimeError("owner compaction is not idle")
    if routing.get("task-id"):
        raise RuntimeError("owner session is attached to a task")

    if any(not isinstance(item, dict) or not item.get("id") for item in sessions):
        raise RuntimeError("owner session catalog has no stable session IDs")
    session_ids = sorted(str(item["id"]) for item in sessions)
    session_identity_hash = hashlib.sha256("\n".join(session_ids).encode()).hexdigest()

    paths = _paths(env, state)
    databases = {}
    lmdb = {}
    for name, directory in paths.items():
        if directory.exists():
            for db in sorted(directory.glob("*.sqlite")):
                databases[f"{name}/{db.name}"] = _database_report(db)
            data_file = directory / "data.mdb"
            if data_file.exists():
                lmdb[name] = {"data_bytes": data_file.stat().st_size,
                              "lock_bytes": (directory / "lock.mdb").stat().st_size
                              if (directory / "lock.mdb").exists() else 0}
    _phase("store-inventory-complete")
    if any(db.get("integrity") != "ok" for db in databases.values()):
        raise RuntimeError("a native SQLite event store failed read-only integrity_check")

    _phase("passed")
    return {
        "preflight": "passed",
        "health_status": health_status,
        "source_state": "/private/tmp/awp-v2-state",
        "supervisor_pid": supervisor_pid,
        "service_names": sorted(services),
        "owner_task_count": len(tasks),
        "owner_task_statuses": task_statuses,
        "owner_session_count": len(sessions),
        "owner_active_session_count": sum(1 for item in sessions
                                           if isinstance(item, dict) and item.get("active")),
        "owner_session_identity_count": len(session_ids),
        "owner_session_identity_sha256": session_identity_hash,
        "latest_typed_turn": latest.get("status", "missing"),
        "compaction_status": context.get("compactionStatus", "missing"),
        "attached_task": bool(routing.get("task-id")),
        "sqlite": databases,
        "lmdb": lmdb,
    }


def main() -> None:
    def timeout(_signum, _frame):
        raise TimeoutError("preflight exceeded 60-second operational bound")

    signal.signal(signal.SIGALRM, timeout)
    signal.alarm(60)
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", type=Path, required=True)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--runtime-root", type=Path, required=True)
    parser.add_argument("--match-script", type=Path, required=True)
    parser.add_argument("--stack-sha256", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(preflight(args.env, args.state, args.runtime_root,
                                   args.match_script, args.stack_sha256), sort_keys=True))
    except Exception as exc:
        detail = str(exc) if isinstance(exc, RuntimeError) else type(exc).__name__
        print(json.dumps({"preflight": "failed", "reason": detail}), file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
