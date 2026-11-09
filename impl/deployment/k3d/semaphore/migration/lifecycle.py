#!/usr/bin/env python3
"""Semaphore-scoped native Grain stop and staged-runtime start controls."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import signal
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import preflight


LEGACY_RUNTIME_SHA256 = "a81a062312e42712636258a8b82a8ebbf3e191e518b1df091d796c65ae132ea5"


def _stack(runtime_root: Path, env_path: Path, expected_sha: str,
           legacy_time_compat: bool = False):
    path = runtime_root / "impl/deployment/stack.py"
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected_sha:
        raise RuntimeError("staged native runtime stack SHA256 mismatch")
    sys.path.insert(0, str(path.parent))
    spec = importlib.util.spec_from_file_location("awp_staged_stack", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    if legacy_time_compat:
        if expected_sha != LEGACY_RUNTIME_SHA256 or hasattr(module, "time"):
            raise RuntimeError("legacy time compatibility is restricted to the pinned a81 runtime missing its import")
        module.time = time
    module.load_env(env_path)
    return module, path


def _stop_source(runtime_root: Path, env_path: Path, state: Path,
                 native_script: Path, expected_sha: str) -> dict:
    stack, _staged_script = _stack(runtime_root, env_path, expected_sha)
    owned = preflight._owned_supervisor_at(stack, state, env_path, native_script.resolve(),
                                            staged_script_arg=runtime_root / "impl/deployment/stack.py",
                                            staged_stack_sha256=expected_sha)
    if owned is None:
        raise RuntimeError("reviewed native Grain supervisor is not running")
    supervisor, children = owned
    processes = stack._ps_table()
    if supervisor not in processes or set(children.values()) - set(processes):
        raise RuntimeError("native supervisor ownership changed during stop preflight")
    os.kill(supervisor, signal.SIGTERM)
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        remaining = stack._ps_table()
        if supervisor not in remaining and not (set(children.values()) & set(remaining)):
            return {"stopped": True, "supervisor_pid": supervisor,
                    "service_names": sorted(children), "force_kill": False}
        time.sleep(0.25)
    raise TimeoutError("native stack did not stop within 45s; no force-kill attempted")


def _start_source(runtime_root: Path, env_path: Path, state: Path,
                  native_script: Path, expected_sha: str,
                  legacy_time_compat: bool = False) -> dict:
    stack, staged_script = _stack(runtime_root, env_path, expected_sha,
                                  legacy_time_compat=legacy_time_compat)
    processes = stack._ps_table()
    candidates = [(pid, proc) for pid, proc in processes.items()
                  if proc[1] == pid and "stack.py run" in proc[2]
                  and str(env_path) in proc[2] and str(state) in proc[2]]
    if candidates:
        if len(candidates) != 1:
            raise RuntimeError("multiple native supervisors match the reviewed service paths")
        _pid, (_ppid, _pgid, command) = candidates[0]
        if str(staged_script) in command:
            owned = stack._owned_supervisor(state, env_path)
        else:
            owned = preflight._owned_supervisor_at(stack, state, env_path, native_script.resolve(),
                                            staged_script_arg=runtime_root / "impl/deployment/stack.py",
                                            staged_stack_sha256=expected_sha)
        if owned is None:
            raise RuntimeError("native supervisor exists without verified service ownership")
        return {"started": True, "already_running": True,
                "supervisor_pid": owned[0], "runtime_script": str(staged_script)}
    result = stack.start_stack(state, env_path, timeout=180)
    result["runtime_script"] = str(staged_script)
    result["already_running"] = False
    return result


def _validate_start(runtime_root: Path, env_path: Path, state: Path,
                    native_script: Path, expected_sha: str,
                    source_runtime_root: Path | None = None,
                    source_stack_sha256: str | None = None) -> dict:
    stack, staged_script = _stack(runtime_root, env_path, expected_sha)
    source_root = source_runtime_root or runtime_root
    source_sha = source_stack_sha256 or expected_sha
    source_stack, source_script = _stack(source_root, env_path, source_sha)
    env = stack.service_environment(env_path, state)
    owned = preflight._owned_supervisor_at(source_stack, state, env_path, native_script.resolve(),
                                            staged_script_arg=source_script,
                                            staged_stack_sha256=source_sha)
    if not owned:
        raise RuntimeError("native source supervisor is not verifiably running")
    specs = stack.native_specs(env)
    return {
        "validation": "passed",
        "staged_stack_sha256": expected_sha,
        "staged_start_script": str(staged_script),
        "source_supervisor_pid": owned[0],
        "service_names": sorted(spec.name for spec in specs),
        "start_command": [sys.executable, str(staged_script), "start",
                          "--env", str(env_path), "--state", str(state)],
        "source_state_untouched": True,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("validate-start", "stop-source", "start-source"))
    parser.add_argument("--runtime-root", type=Path, required=True)
    parser.add_argument("--env", type=Path, required=True)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--match-script", type=Path)
    parser.add_argument("--stack-sha256", required=True)
    parser.add_argument("--source-runtime-root", type=Path)
    parser.add_argument("--source-stack-sha256")
    parser.add_argument("--legacy-time-compat", action="store_true")
    args = parser.parse_args()
    runtime = args.runtime_root.resolve()
    env = args.env.resolve()
    state = args.state.resolve()
    expected_root = Path.home() / "Library/Application Support/AWPv2/k3d/semaphore"
    if runtime.parent != expected_root or not runtime.name.endswith("-full"):
        raise SystemExit("refusing a staged runtime outside the reviewed Application Support root")
    if env != Path("/private/tmp/awp-v2-deploy-config/service.env") or state != Path("/private/tmp/awp-v2-state"):
        raise SystemExit("refusing service paths outside the reviewed native v2 state")
    if args.action in ("validate-start", "stop-source"):
        if not args.match_script:
            raise SystemExit("--match-script is required for source verification")
        match = args.match_script.resolve()
        if match != preflight.EXPECTED_NATIVE_SCRIPT:
            raise SystemExit("native stack match path differs from the reviewed source")
    else:
        match = args.match_script.resolve() if args.match_script else preflight.EXPECTED_NATIVE_SCRIPT
    if args.legacy_time_compat and args.action != "start-source":
        raise SystemExit("legacy time compatibility is allowed only for start-source")
    if args.action == "validate-start":
        if bool(args.source_runtime_root) != bool(args.source_stack_sha256):
            raise SystemExit("source runtime root and SHA256 must be provided together")
        result = _validate_start(runtime, env, state, match, args.stack_sha256,
                                 args.source_runtime_root.resolve() if args.source_runtime_root else None,
                                 args.source_stack_sha256)
    elif args.action == "stop-source":
        result = _stop_source(runtime, env, state, match, args.stack_sha256)
    else:
        result = _start_source(runtime, env, state, match, args.stack_sha256,
                               legacy_time_compat=args.legacy_time_compat)
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
