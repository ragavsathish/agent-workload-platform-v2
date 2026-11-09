#!/usr/bin/env python3
"""Cold, verified SQLite/LMDB snapshot into the retained Grain candidate PV."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import sqlite3
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import preflight

KUBECTL = "/usr/local/bin/kubectl"


def _sqlite_backup(source: Path, destination: Path) -> dict:
    destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    src = sqlite3.connect(f"file:{source}?mode=ro", uri=True, timeout=5)
    dst = sqlite3.connect(destination, timeout=5)
    try:
        src.execute("PRAGMA query_only=ON")
        src.backup(dst)
    finally:
        dst.close()
        src.close()
    destination.chmod(0o600)
    return preflight._database_report(destination)


def _signature(report: dict) -> dict:
    return {key: report.get(key) for key in (
        "integrity", "events", "event_tags", "tenants", "tenant_hashes", "event_ids_sha256",
    )}


def _secure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.is_symlink() or not path.is_dir():
        raise RuntimeError(f"refusing a non-directory migration path: {path.name}")
    path.chmod(0o700)


def _regular_files(root: Path):
    if root.is_symlink() or not root.is_dir():
        raise RuntimeError("migration tree root is not a regular directory")
    for path in sorted(root.rglob("*")):
        mode = path.lstat().st_mode
        if stat.S_ISLNK(mode):
            raise RuntimeError(f"refusing symlink in migration tree: {path.relative_to(root)}")
        if not (stat.S_ISDIR(mode) or stat.S_ISREG(mode)):
            raise RuntimeError(f"refusing special file in migration tree: {path.relative_to(root)}")
        if stat.S_ISREG(mode):
            yield path


def _assert_candidate_stopped() -> None:
    scale = subprocess.check_output([
        KUBECTL, "--context", "k3d-awp-v2-ops", "-n", "awp-v2",
        "get", "statefulset", "grain", "-o", "jsonpath={.spec.replicas}",
    ], text=True).strip()
    if scale != "0":
        raise RuntimeError("candidate Grain StatefulSet is not scaled to zero")
    pod = subprocess.run([
        KUBECTL, "--context", "k3d-awp-v2-ops", "-n", "awp-v2",
        "get", "pod", "grain-0", "-o", "name",
    ], capture_output=True, text=True, check=False)
    if pod.returncode == 0 or "NotFound" not in pod.stderr:
        raise RuntimeError("candidate Grain pod is still present or its absence could not be verified")


def _sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _copy_tree_quiesced(source_root: Path, destination_root: Path,
                        names: dict[str, Path], destination_dirs: dict[str, Path]) -> dict:
    reports = {"sqlite": {}, "lmdb": {}, "other_files": 0}
    _secure_dir(destination_root)
    for name, directory in names.items():
        if not directory.exists():
            continue
        if directory.is_symlink() or not directory.is_dir():
            raise RuntimeError(f"refusing symlink or non-directory migration source: {name}")
        relative_dir = destination_dirs[name]
        out_dir = destination_root / relative_dir
        _secure_dir(out_dir)
        for item in sorted(directory.rglob("*")):
            mode = item.lstat().st_mode
            if stat.S_ISLNK(mode) or not (stat.S_ISDIR(mode) or stat.S_ISREG(mode)):
                raise RuntimeError(f"refusing symlink or special file in migration tree: {item.relative_to(source_root)}")
            if stat.S_ISDIR(mode):
                _secure_dir(destination_root / relative_dir / item.relative_to(directory))
        for src in _regular_files(directory):
            rel = relative_dir / src.relative_to(directory)
            dst = destination_root / rel
            if src.name == "lock.mdb" or src.name.endswith(("-wal", "-shm")):
                continue
            if src.suffix == ".sqlite":
                reports["sqlite"][str(rel)] = _sqlite_backup(src, dst)
            elif src.name == "data.mdb":
                _secure_dir(dst.parent)
                shutil.copy2(src, dst)
                dst.chmod(0o600)
                reports["lmdb"][str(rel)] = {"size_bytes": dst.stat().st_size,
                                             "sha256": _sha(dst)}
            else:
                _secure_dir(dst.parent)
                shutil.copy2(src, dst)
                dst.chmod(0o600)
                reports["other_files"] += 1
    return reports


def detect_candidate_layout(target: Path, state: Path, paths: dict[str, Path]) -> str:
    if target.is_symlink() or not target.is_dir():
        raise RuntimeError("candidate PV root is not a regular directory")
    entries = list(target.iterdir())
    if any(item.is_symlink() or not item.is_dir() for item in entries):
        raise RuntimeError("candidate PV contains an unexpected top-level path")
    names = {item.name for item in entries}
    canonical = set(paths)
    native = {path.relative_to(state).parts[0] for path in paths.values()}
    canonical_only = names <= canonical and bool(names & (canonical - native))
    native_only = names <= native and bool(names & (native - canonical))
    if canonical_only and not native_only:
        return "canonical"
    if native_only and not canonical_only:
        return "native"
    raise RuntimeError("candidate PV layout is ambiguous or contains an unrecognized store path")


def _copy_complete_tree(source: Path, destination: Path) -> dict[str, int]:
    if source.is_symlink() or not source.is_dir():
        raise RuntimeError("candidate rollback source is not a regular directory")
    _secure_dir(destination)
    count = 0
    for item in sorted(source.rglob("*")):
        mode = item.lstat().st_mode
        if stat.S_ISLNK(mode):
            raise RuntimeError(f"refusing symlink in candidate rollback tree: {item.relative_to(source)}")
        dst = destination / item.relative_to(source)
        if stat.S_ISDIR(mode):
            _secure_dir(dst)
        elif stat.S_ISREG(mode):
            _secure_dir(dst.parent)
            shutil.copy2(item, dst)
            dst.chmod(0o600)
            count += 1
        else:
            raise RuntimeError(f"refusing special file in candidate rollback tree: {item.relative_to(source)}")
    return {"files": count}


def _manifest(backup: Path) -> tuple[str, int]:
    records = []
    for scope in (backup / "source-state", backup / "candidate-before"):
        for path in _regular_files(scope):
            rel = path.relative_to(backup)
            records.append({"path": str(rel), "size_bytes": path.stat().st_size,
                            "sha256": _sha(path)})
    records.sort(key=lambda item: item["path"])
    manifest = backup / "manifest.json"
    manifest.write_text(json.dumps(records, sort_keys=True, separators=(",", ":")) + "\n")
    manifest.chmod(0o600)
    return _sha(manifest), len(records)


def _validate_offline(state: Path, runtime_root: Path, env_path: Path,
                      expected_sha: str) -> tuple[object, dict[str, Path]]:
    stack_path = runtime_root / "impl/deployment/stack.py"
    expected = hashlib.sha256(stack_path.read_bytes()).hexdigest()
    if expected != expected_sha:
        raise RuntimeError("staged runtime stack SHA256 does not match the pinned value")
    sys.path.insert(0, str(stack_path.parent))
    import importlib.util
    spec = importlib.util.spec_from_file_location("awp_migration_stack", stack_path)
    stack = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(stack)
    env = stack.load_env(env_path)
    paths = preflight._paths(env, state)
    processes = stack._ps_table()
    expected_env = str(env_path)
    expected_state = str(state)
    live = [pid for pid, (_ppid, pgid, cmd) in processes.items()
            if pgid == pid and "stack.py run" in cmd
            and expected_env in cmd and expected_state in cmd]
    if live:
        raise RuntimeError("native Grain supervisor is still running; no data copy started")
    return stack, paths


def _perform_transaction(target: Path, backup: Path, state: Path,
                        paths: dict[str, Path]) -> dict:
    if not target.is_dir():
        raise RuntimeError("retained candidate PV directory is missing")
    source_snapshot = backup / "source-state"
    candidate_before = backup / "candidate-before"
    candidate_before_staging = backup / "candidate-before-staging"
    candidate_layout = detect_candidate_layout(target, state, paths)
    candidate_relative_dirs = {
        name: Path(name) if candidate_layout == "canonical" else path.relative_to(state)
        for name, path in paths.items()
    }
    candidate_paths = {name: target / relative for name, relative in candidate_relative_dirs.items()}
    candidate_before_report = _copy_tree_quiesced(
        target, candidate_before_staging, candidate_paths, candidate_relative_dirs)
    for rel, report in candidate_before_report["sqlite"].items():
        if _signature(preflight._database_report(target / rel)) != _signature(report):
            raise RuntimeError(f"candidate rollback SQLite snapshot mismatch at {rel}")
    for rel, report in candidate_before_report["lmdb"].items():
        if _sha(target / rel) != report["sha256"] or (target / rel).stat().st_size != report["size_bytes"]:
            raise RuntimeError(f"candidate rollback LMDB snapshot mismatch at {rel}")
    os.replace(candidate_before_staging, candidate_before)
    source_relative_dirs = {name: path.relative_to(state) for name, path in paths.items()}
    source_report = _copy_tree_quiesced(state, source_snapshot, paths, source_relative_dirs)
    staging = target.parent / f".{target.name}.slice44-staging"
    if staging.exists():
        raise RuntimeError("migration staging path already exists")
    candidate_report = _copy_tree_quiesced(state, staging, paths, source_relative_dirs)
    if source_report != candidate_report:
        raise RuntimeError("source snapshot and candidate staging summaries differ")
    for rel, report in source_report["sqlite"].items():
        staged_report = preflight._database_report(staging / rel)
        if _signature(report) != _signature(staged_report):
            raise RuntimeError(f"staged SQLite state mismatch at {rel}")
    for rel, report in source_report["lmdb"].items():
        staged_path = staging / rel
        if staged_path.stat().st_size != report["size_bytes"] or _sha(staged_path) != report["sha256"]:
            raise RuntimeError(f"staged LMDB data mismatch at {rel}")
    if not source_report["sqlite"].get("sessions-deployment/deployment-events.sqlite", {}).get("events"):
        raise RuntimeError("native deployment event stream is empty")
    if not source_report["sqlite"].get("sessions/session-events.sqlite", {}).get("events"):
        raise RuntimeError("native session event stream is empty")
    manifest_hash, manifest_files = _manifest(backup)
    previous_live = target.parent / f".{target.name}.slice44-before"
    if previous_live.exists():
        raise RuntimeError("previous candidate exchange path already exists")
    os.replace(target, previous_live)
    try:
        os.replace(staging, target)
    except Exception:
        os.replace(previous_live, target)
        raise
    shutil.rmtree(previous_live)
    return {
        "migration": "copied-and-verified",
        "candidate_before_layout": candidate_layout,
        "backup_path": str(backup),
        "candidate_before_backup": str(candidate_before),
        "manifest_sha256": manifest_hash,
        "manifest_file_count": manifest_files,
        "candidate_before_sqlite": {key: _signature(value) for key, value in candidate_before_report["sqlite"].items()},
        "candidate_before_lmdb": candidate_before_report["lmdb"],
        "sqlite": {key: _signature(value) for key, value in source_report["sqlite"].items()},
        "lmdb": source_report["lmdb"],
        "other_files": source_report["other_files"],
    }


def _perform(target: Path, backup: Path, state: Path, paths: dict[str, Path]) -> dict:
    if backup.exists():
        raise RuntimeError("migration backup already exists; refusing to overwrite it")
    _secure_dir(backup.parent)
    backup.mkdir(mode=0o700)
    backup.chmod(0o700)
    staging = target.parent / f".{target.name}.slice44-staging"
    previous = target.parent / f".{target.name}.slice44-before"
    try:
        return _perform_transaction(target, backup, state, paths)
    except Exception:
        if previous.exists() and not target.exists():
            os.replace(previous, target)
        if staging.exists():
            shutil.rmtree(staging)
        # If target and previous both exist, the new candidate was swapped; retain
        # the complete rollback snapshot for the Ansible rescue task.
        if backup.exists() and not (previous.exists() and target.exists()):
            shutil.rmtree(backup)
        raise


def _restore(target: Path, backup: Path) -> dict:
    _assert_candidate_stopped()
    source = backup / "candidate-before"
    if not source.is_dir():
        raise RuntimeError("rollback candidate backup is missing")
    failed = target.parent / f".{target.name}.failed-slice44"
    if failed.exists():
        raise RuntimeError("failed candidate rollback staging path already exists")
    if target.exists():
        os.replace(target, failed)
    try:
        _copy_complete_tree(source, target)
    except Exception:
        if failed.exists() and not target.exists():
            os.replace(failed, target)
        raise
    if failed.exists():
        shutil.rmtree(failed)
    return {"rollback": "candidate-before-restored", "backup_path": str(source)}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("migrate", "restore"))
    parser.add_argument("--env", type=Path, required=True)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--runtime-root", type=Path, required=True)
    parser.add_argument("--target", type=Path, required=True)
    parser.add_argument("--backup", type=Path, required=True)
    parser.add_argument("--stack-sha256", required=True)
    args = parser.parse_args()
    state = args.state.resolve()
    env = args.env.resolve()
    runtime = args.runtime_root.resolve()
    target = args.target.resolve()
    backup = args.backup.resolve()
    expected_root = Path.home() / "Library/Application Support/AWPv2"
    if state != Path("/private/tmp/awp-v2-state") or env != Path("/private/tmp/awp-v2-deploy-config/service.env"):
        raise SystemExit("refusing native state paths outside the reviewed v2 source")
    if target != expected_root / "k3d/storage/awp-v2-grain-candidate/grain-candidate":
        raise SystemExit("refusing a candidate target outside the retained Grain PVC directory")
    if (backup.parent != expected_root / "migrations"
            or not re.fullmatch(r"grain-state-slice44-2026-11-09-task-\d+", backup.name)):
        raise SystemExit("refusing a backup path outside the durable migration root")
    if args.action == "restore":
        result = _restore(target, backup)
    else:
        _assert_candidate_stopped()
        _stack, paths = _validate_offline(state, runtime, env, args.stack_sha256)
        result = _perform(target, backup, state, paths)
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
