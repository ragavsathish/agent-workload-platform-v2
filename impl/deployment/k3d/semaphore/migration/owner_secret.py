#!/usr/bin/env python3
"""Set the private Grain owner Secret without disclosing owner or token values."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ENV = Path("/private/tmp/awp-v2-deploy-config/service.env")
TOKEN = Path.home() / "Library/Application Support/AWPv2/k3d/grain-candidate/session-token"
CONTEXT = "k3d-awp-v2-ops"
NAMESPACE = "awp-v2"
KUBECTL = "/usr/local/bin/kubectl"
RUNTIME_ROOT = Path.home() / "Library/Application Support/AWPv2/k3d/semaphore"


def _load_env(runtime_root: Path, expected_sha: str) -> dict[str, str]:
    root = runtime_root.resolve()
    if root.parent != RUNTIME_ROOT or root.name != f"source-{expected_sha}-full":
        raise SystemExit("runtime root is outside the reviewed Application Support path")
    stack_path = root / "impl/deployment/stack.py"
    if hashlib.sha256(stack_path.read_bytes()).hexdigest() != expected_sha:
        raise SystemExit("staged runtime stack SHA256 mismatch")
    spec = importlib.util.spec_from_file_location("awp_owner_secret_stack", stack_path)
    sys.path.insert(0, str(stack_path.parent))
    stack = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(stack)
    return stack.load_env(ENV)


def _private_file(prefix: str, value: str) -> Path:
    fd, filename = tempfile.mkstemp(prefix=prefix)
    path = Path(filename)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        stream.write(value)
    return path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidate-default", action="store_true")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--runtime-root", type=Path, required=True)
    parser.add_argument("--stack-sha256", required=True)
    args = parser.parse_args()

    values = _load_env(args.runtime_root, args.stack_sha256)
    if args.check:
        if args.candidate_default:
            raise SystemExit("--check cannot be combined with --candidate-default")
        if not TOKEN.is_file():
            raise SystemExit("candidate token input is missing")
        candidate_token = TOKEN.read_text(encoding="utf-8").strip()
        print(f"secret_input_check=passed owner_loaded={bool(values.get('AWP_OWNER_IDENTITY'))} "
              f"candidate_token_nonempty={bool(candidate_token)} "
              f"candidate_token_trailing_whitespace={TOKEN.read_text(encoding='utf-8') != TOKEN.read_text(encoding='utf-8').strip()}")
        return
    owner = "awp-v2-candidate" if args.candidate_default else values.get("AWP_OWNER_IDENTITY")
    if not owner or not TOKEN.is_file():
        raise SystemExit("native owner or candidate token input is missing")

    owner_path = _private_file("awp-v2-grain-owner-", owner)
    normalized_token_path = None
    try:
        if args.candidate_default:
            token_path = TOKEN
        else:
            # The candidate probe strips its token; avoid injecting the token file's newline
            # into the pod environment, where Grain compares the configured token exactly.
            candidate_token = TOKEN.read_text(encoding="utf-8").strip()
            if not candidate_token:
                raise SystemExit("candidate token input is empty after normalization")
            normalized_token_path = _private_file("awp-v2-grain-token-", candidate_token)
            token_path = normalized_token_path
        create = subprocess.run([
            KUBECTL, "--context", CONTEXT, "-n", NAMESPACE,
            "create", "secret", "generic", "grain-session",
            f"--from-file=token={token_path}", f"--from-file=owner={owner_path}",
            "--dry-run=client", "-o", "yaml",
        ], capture_output=True, check=True)
        subprocess.run([
            KUBECTL, "--context", CONTEXT, "-n", NAMESPACE, "apply", "-f", "-",
        ], input=create.stdout, check=True, stdout=subprocess.DEVNULL)
        owner_kind = "candidate-default" if args.candidate_default else "native-owner"
        token_kind = "restored" if args.candidate_default else "normalized-candidate"
        print(f"candidate_owner_secret=updated token={token_kind} owner_source={owner_kind}")
    finally:
        owner_path.unlink(missing_ok=True)
        if normalized_token_path is not None:
            normalized_token_path.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
