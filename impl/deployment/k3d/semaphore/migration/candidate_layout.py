#!/usr/bin/env python3
"""Read-only, hash-pinned inspection of the candidate PVC's on-disk layout."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import re
import sys
from pathlib import Path

import preflight
import migrate_state

EXPECTED_ENV = Path("/private/tmp/awp-v2-deploy-config/service.env")
EXPECTED_STATE = Path("/private/tmp/awp-v2-state")
EXPECTED_TARGET = Path.home() / "Library/Application Support/AWPv2/k3d/storage/awp-v2-grain-candidate/grain-candidate"
RUNTIME_ROOT = Path.home() / "Library/Application Support/AWPv2/k3d/semaphore"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", type=Path, required=True)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--target", type=Path, required=True)
    parser.add_argument("--runtime-root", type=Path, required=True)
    parser.add_argument("--stack-sha256", required=True)
    args = parser.parse_args()

    env_path = args.env.resolve()
    state = args.state.resolve()
    target = args.target.resolve()
    runtime_root = args.runtime_root.resolve()
    if env_path != EXPECTED_ENV or state != EXPECTED_STATE or target != EXPECTED_TARGET:
        raise SystemExit("candidate layout inspection is outside reviewed paths")
    if runtime_root.parent != RUNTIME_ROOT or runtime_root.name != f"source-{args.stack_sha256}-full":
        raise SystemExit("candidate layout runtime path does not match the pinned digest")

    stack_path = runtime_root / "impl/deployment/stack.py"
    if hashlib.sha256(stack_path.read_bytes()).hexdigest() != args.stack_sha256:
        raise SystemExit("staged runtime stack SHA256 mismatch")
    sys.path.insert(0, str(stack_path.parent))
    spec = importlib.util.spec_from_file_location("awp_candidate_layout_stack", stack_path)
    stack = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(stack)
    env = stack.load_env(env_path)
    paths = preflight._paths(env, state)
    layout = migrate_state.detect_candidate_layout(target, state, paths)

    manifest = Path(__file__).resolve().with_name("grain.yaml").read_text(encoding="utf-8")
    path_variables = {
        "AWP_SESSION_STORAGE_DIR": "sessions",
        "AWP_TASKING_STORAGE_DIR": "tasking",
        "AWP_TASK_WORKSPACE_DIR": "task-workspaces",
        "AWP_DEPLOYMENT_STORAGE_DIR": "deployment",
        "AWP_KNOWLEDGE_STORAGE_DIR": "knowledge",
        "AWP_SONG_STORAGE_DIR": "song",
        "AWP_INCIDENT_STORAGE_DIR": "incidents",
        "AWP_SONG_ARTIFACT_DIR": "song-artifacts",
    }
    env_paths_match = True
    for variable, directory in path_variables.items():
        match = re.search(rf"(?m)^\s+- name: {re.escape(variable)}\s*\n\s+value: ([^\n]+)$", manifest)
        env_paths_match = env_paths_match and bool(match and match.group(1).strip() == str(paths[directory]))
    subpath_matches = len(re.findall(
        r"(?m)^\s+mountPath: /private/tmp/awp-v2-state\s*\n\s+subPath: grain-candidate\s*$",
        manifest)) == 2
    claim_matches = ("name: grain-data-stable" in manifest and
                     "volumeName: awp-v2-grain-candidate" in manifest and
                     "persistentVolumeReclaimPolicy: Retain" in manifest)
    pv_mount_matches = "/var/lib/rancher/k3s/storage/awp-v2-grain-candidate" in manifest
    report = {"layout": layout, "root_count": len(list(target.iterdir())),
              "native_env_paths_match": env_paths_match,
              "volume_subpath_matches": subpath_matches,
              "claim_and_retained_pv_match": claim_matches and pv_mount_matches}
    if not (env_paths_match and subpath_matches and claim_matches and pv_mount_matches):
        raise SystemExit("candidate manifest paths do not match native storage identity")
    print(json.dumps(report, sort_keys=True))


if __name__ == "__main__":
    main()
