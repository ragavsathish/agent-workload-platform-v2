#!/usr/bin/env python3
"""Loopback-only owner/session visibility probe for the k3d Grain candidate."""
from __future__ import annotations

import base64
import hashlib
import importlib.util
import json
import re
import sqlite3
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

CONTEXT = "k3d-awp-v2-ops"
NAMESPACE = "awp-v2"
LOCAL_PORT = 19447
KUBECTL = "/usr/local/bin/kubectl"


def _env(env_path: Path, runtime_root: Path, expected_sha: str) -> dict[str, str]:
    stack_path = runtime_root / "impl/deployment/stack.py"
    if hashlib.sha256(stack_path.read_bytes()).hexdigest() != expected_sha:
        raise RuntimeError("staged runtime stack SHA256 mismatch")
    sys.path.insert(0, str(stack_path.parent))
    spec = importlib.util.spec_from_file_location("awp_probe_stack", stack_path)
    stack = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(stack)
    return stack.load_env(env_path)


def _token() -> str:
    encoded = subprocess.check_output([
        KUBECTL, "--context", CONTEXT, "-n", NAMESPACE, "get", "secret", "grain-session",
        "-o", "jsonpath={.data.token}",
    ], text=True).strip()
    return base64.b64decode(encoded).decode().strip()


def _post(base: str, token: str, owner: str, path: str) -> dict:
    request = urllib.request.Request(
        base.rstrip("/") + path, data=b"{}",
        headers={"Authorization": f"Bearer {token}", "X-AWP-Owner": owner,
                 "Content-Type": "application/json"}, method="POST",
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.load(response)


def _read_event_ids(path: Path) -> tuple[set[tuple[str, str]], list[str]]:
    if not path.is_file():
        return set(), []
    conn = sqlite3.connect(f"file:{path}?mode=ro", uri=True, timeout=5)
    try:
        conn.execute("PRAGMA query_only=ON")
        tables = {row[0] for row in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'")}
        pairs = set()
        if "events" in tables:
            pairs = {(str(tenant), str(event_id)) for tenant, event_id in conn.execute(
                "SELECT tenant_id,id FROM events")}
        tenants = []
        if "tenants" in tables:
            tenants = [str(row[0]) for row in conn.execute("SELECT id FROM tenants ORDER BY id")]
        return pairs, sorted(hashlib.sha256(x.encode()).hexdigest()[:16] for x in tenants)
    finally:
        conn.close()


def _store_continuity(snapshot: Path, candidate: Path) -> dict:
    source_dbs = sorted(snapshot.rglob("*.sqlite"))
    rows = []
    all_preserved = True
    source_total = candidate_total = 0
    for source in source_dbs:
        rel = source.relative_to(snapshot)
        target = candidate / rel
        source_ids, source_tenants = _read_event_ids(source)
        candidate_ids, candidate_tenants = _read_event_ids(target)
        tenant_hashes_match = source_tenants == candidate_tenants
        preserved = source_ids <= candidate_ids and tenant_hashes_match and target.is_file()
        all_preserved = all_preserved and preserved
        source_total += len(source_ids)
        candidate_total += len(candidate_ids)
        rows.append({"store": str(rel), "source_events": len(source_ids),
                     "candidate_events": len(candidate_ids),
                     "source_event_ids_preserved": preserved,
                     "tenant_hashes_match": tenant_hashes_match,
                     "source_tenant_hashes": source_tenants,
                     "candidate_tenant_hashes": candidate_tenants})
    return {"database_count": len(rows), "source_event_total": source_total,
            "candidate_event_total": candidate_total,
            "source_event_ids_preserved": all_preserved,
            "source_tenant_hashes_match": all(row["tenant_hashes_match"] for row in rows),
            "stores": rows}


def main() -> None:
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", type=Path, required=True)
    parser.add_argument("--runtime-root", type=Path, required=True)
    parser.add_argument("--stack-sha256", required=True)
    parser.add_argument("--source-snapshot", type=Path)
    args = parser.parse_args()
    runtime_root = args.runtime_root.resolve()
    expected_root = Path.home() / "Library/Application Support/AWPv2/k3d/semaphore"
    if runtime_root.parent != expected_root or not runtime_root.name.endswith("-full"):
        raise RuntimeError("candidate probe runtime is outside the reviewed Application Support path")
    env = _env(args.env.resolve(), runtime_root, args.stack_sha256)
    token = _token()
    port = subprocess.Popen([
        KUBECTL, "--context", CONTEXT, "-n", NAMESPACE,
        "port-forward", "--address", "127.0.0.1", "service/grain",
        f"{LOCAL_PORT}:9447",
    ], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if port.poll() is not None:
                raise RuntimeError("candidate loopback port-forward exited early")
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{LOCAL_PORT}/health", timeout=1) as response:
                    if response.status == 200:
                        break
            except Exception:
                time.sleep(0.2)
        else:
            raise TimeoutError("candidate loopback health check timed out")
        native_base = env.get("AWP_GRAIN_SESSION_URL", f"http://127.0.0.1:{env.get('AWP_GRAIN_SESSION_PORT', '9747')}")
        native_catalog = _post(native_base, env["AWP_GRAIN_SESSION_TOKEN"],
                               env["AWP_OWNER_IDENTITY"], "/internal/session/catalog")
        native_sessions = native_catalog.get("sessions", []) if isinstance(native_catalog, dict) else []
        native_tasks_reply = _post(native_base, env["AWP_GRAIN_SESSION_TOKEN"],
                                   env["AWP_OWNER_IDENTITY"], "/internal/task/list")
        native_tasks = native_tasks_reply.get("tasks", []) if isinstance(native_tasks_reply, dict) else native_tasks_reply
        if any(not isinstance(item, dict) or not item.get("id") for item in native_sessions):
            raise RuntimeError("native session catalog has no stable session IDs")
        native_ids = sorted(str(item["id"]) for item in native_sessions)
        native_hash = hashlib.sha256("\n".join(native_ids).encode()).hexdigest()
        continuity = None
        if args.source_snapshot:
            snapshot = args.source_snapshot.resolve()
            allowed = Path.home() / "Library/Application Support/AWPv2/migrations"
            if (snapshot.parent.parent != allowed or snapshot.name != "source-state"
                    or not re.fullmatch(r"grain-state-slice44-2026-11-09-task-\d+", snapshot.parent.name)):
                raise RuntimeError("source continuity snapshot is outside the reviewed migration path")
            candidate_root = Path.home() / "Library/Application Support/AWPv2/k3d/storage/awp-v2-grain-candidate/grain-candidate"
            continuity = _store_continuity(snapshot, candidate_root)
        try:
            catalog = _post(f"http://127.0.0.1:{LOCAL_PORT}", token,
                            env["AWP_OWNER_IDENTITY"], "/internal/session/catalog")
            sessions = catalog.get("sessions", []) if isinstance(catalog, dict) else []
            tasks_reply = _post(f"http://127.0.0.1:{LOCAL_PORT}", token,
                                env["AWP_OWNER_IDENTITY"], "/internal/task/list")
            tasks = tasks_reply.get("tasks", []) if isinstance(tasks_reply, dict) else tasks_reply
            if any(not isinstance(item, dict) or not item.get("id") for item in sessions):
                raise RuntimeError("candidate session catalog has no stable session IDs")
            session_ids = sorted(str(item["id"]) for item in sessions)
            session_hash = hashlib.sha256("\n".join(session_ids).encode()).hexdigest()
            print(json.dumps({
                "candidate_owner_query": "accepted",
                "session_count": len(sessions) if isinstance(sessions, list) else -1,
                "active_session_count": sum(1 for x in sessions if isinstance(x, dict) and x.get("active")),
                "session_identity_sha256": session_hash,
                "session_identity_count": len(session_ids),
                "task_count": len(tasks) if isinstance(tasks, list) else -1,
                "native_session_count": len(native_sessions),
                "native_active_session_count": sum(1 for x in native_sessions if isinstance(x, dict) and x.get("active")),
                "native_session_identity_sha256": native_hash,
                "native_session_identity_count": len(native_ids),
                "native_task_count": len(native_tasks) if isinstance(native_tasks, list) else -1,
                "session_identity_match": bool(session_ids == native_ids),
                "store_continuity": continuity,
            }, sort_keys=True))
        except urllib.error.HTTPError as exc:
            print(json.dumps({"candidate_owner_query": "rejected", "http_status": exc.code,
                              "native_session_count": len(native_sessions),
                              "native_session_identity_sha256": native_hash,
                              "native_session_identity_count": len(native_ids),
                              "native_task_count": len(native_tasks) if isinstance(native_tasks, list) else -1}))
    finally:
        port.terminate()
        try:
            port.wait(timeout=5)
        except subprocess.TimeoutExpired:
            raise RuntimeError("candidate port-forward did not exit after TERM")


if __name__ == "__main__":
    main()
