#!/usr/bin/env python3
"""Create an isolated Semaphore project for the v2 host voice sidecar."""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[5]
SOURCE = Path(__file__).resolve().parent
STATE_ROOT = Path.home() / "Library/Application Support/AWPv2/k3d/semaphore"
BARE_REPOSITORY = STATE_ROOT / "voice-sidecar-playbooks.git"
PROJECT_NAME = "AWP v2 Voice Sidecar"
REPOSITORY_NAME = "awp-v2-voice-sidecar-playbooks"
INVENTORY_NAME = "macos-host-local"
ENVIRONMENT_NAME = "awp-v2-voice-sidecar"
VIEW_NAME = "AWP v2 Voice Sidecar"
RUNNER_TAG = "macos-host"
PLAYBOOKS = ("preflight.yml", "start.yml", "check-ready.yml", "status.yml", "stop.yml")


def _git(*args: str, cwd: Path) -> str:
    return subprocess.check_output(["git", *args], cwd=cwd, text=True).strip()


def _sidecar_api():
    migration = SOURCE.parent / "migration" / "setup_project.py"
    spec = importlib.util.spec_from_file_location("awp_semaphore_migration_api", migration)
    if spec is None or spec.loader is None:
        raise RuntimeError("Semaphore API client could not be loaded")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.Semaphore()


def _create_repository() -> tuple[str, str]:
    STATE_ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    STATE_ROOT.chmod(0o700)
    parent = Path(tempfile.mkdtemp(prefix="awp-v2-voice-sidecar-", dir="/private/tmp"))
    work = parent / "repo"
    try:
        if BARE_REPOSITORY.exists():
            _git("clone", str(BARE_REPOSITORY), str(work), cwd=parent)
        else:
            work.mkdir(mode=0o700)
            _git("init", "-b", "main", cwd=work)
            _git("config", "user.name", "AWP v2 Operations", cwd=work)
            _git("config", "user.email", "awp-v2-ops@localhost", cwd=work)
        for name in (*PLAYBOOKS, "host_voice_sidecar.yml", "host_voice_sidecar.py", "setup_project.py",
                     "voice-sidecar.env.example", "README.md"):
            shutil.copy2(SOURCE / name, work / name)
        _git("add", *PLAYBOOKS, "host_voice_sidecar.yml", "host_voice_sidecar.py",
             "voice-sidecar.env.example", "README.md", cwd=work)
        changed = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=work).returncode != 0
        if changed:
            _git("config", "user.name", "AWP v2 Operations", cwd=work)
            _git("config", "user.email", "awp-v2-ops@localhost", cwd=work)
            _git("commit", "-m", "Update v2 voice sidecar operations", cwd=work)
        revision = _git("rev-parse", "HEAD", cwd=work)
        if BARE_REPOSITORY.exists():
            _git("push", "origin", "main", cwd=work)
        else:
            _git("clone", "--bare", str(work), str(BARE_REPOSITORY), cwd=parent)
        _git("--git-dir", str(BARE_REPOSITORY), "update-ref", f"refs/heads/pin-{revision}",
             revision, cwd=STATE_ROOT)
        os.chmod(BARE_REPOSITORY, 0o700)
        return BARE_REPOSITORY.as_uri(), revision
    finally:
        shutil.rmtree(parent, ignore_errors=True)


def provision(git_url: str, revision: str, runtime_root: Path, stack_sha256: str) -> dict:
    api = _sidecar_api()
    projects = api._request("GET", "/api/projects") or []
    found = [item for item in projects if item.get("name") == PROJECT_NAME]
    if len(found) > 1:
        raise RuntimeError("duplicate v2 voice sidecar projects exist")
    project = found[0] if found else api._request("POST", "/api/projects", {
        "name": PROJECT_NAME, "alert": False, "max_parallel_tasks": 1, "demo": False,
    })
    project_id = project["id"]
    prefix = f"/api/project/{project_id}"
    pinned_branch = f"pin-{revision}"

    repository = api._find_or_create("POST", prefix + "/repositories", REPOSITORY_NAME, {
        "project_id": project_id, "name": REPOSITORY_NAME,
        "git_url": git_url, "git_branch": pinned_branch, "ssh_key_id": 1,
    })
    if repository.get("git_url") != git_url:
        raise RuntimeError("voice sidecar repository URL does not match its local source")
    if repository.get("git_branch") != pinned_branch:
        api._request("PUT", prefix + f"/repositories/{repository['id']}", {
            **repository, "git_branch": pinned_branch,
        })

    inventory = api._find_or_create("POST", prefix + "/inventory", INVENTORY_NAME, {
        "project_id": project_id,
        "name": INVENTORY_NAME,
        "inventory": "all:\n  hosts:\n    localhost:\n      ansible_connection: local\n",
        "type": "static-yaml",
        "runner_tag": RUNNER_TAG,
    })
    environment_json = json.dumps({
        "awp_runtime_root": str(runtime_root),
        "awp_stack_sha256": stack_sha256,
    }, sort_keys=True)
    environment = api._find_or_create("POST", prefix + "/environment", ENVIRONMENT_NAME, {
        "project_id": project_id, "name": ENVIRONMENT_NAME,
        "json": environment_json, "env": "{}",
    })
    if environment.get("json") != environment_json:
        api._request("PUT", prefix + f"/environment/{environment['id']}", {
            **environment, "json": environment_json,
        })

    views = api._request("GET", prefix + "/views") or []
    matches = [item for item in views if item.get("title") == VIEW_NAME or item.get("name") == VIEW_NAME]
    if len(matches) > 1:
        raise RuntimeError("duplicate v2 voice sidecar views exist")
    if matches:
        view = matches[0]
    else:
        default = [item for item in views if item.get("title") == "All" or item.get("name") == "All"]
        if not default:
            raise RuntimeError("Semaphore project has no default template view")
        view = default[0]

    template_url = prefix + "/templates"
    templates = api._request("GET", template_url + "?sort=name&order=asc") or []
    template_ids = {}
    for playbook in PLAYBOOKS:
        action = playbook.removesuffix(".yml")
        name = f"AWP v2 voice sidecar {action}"
        body = {
            "project_id": project_id,
            "inventory_id": inventory["id"],
            "repository_id": repository["id"],
            "environment_id": environment["id"],
            "view_id": view["id"],
            "name": name,
            "playbook": playbook,
            "app": "ansible",
            "git_branch": pinned_branch,
            "runner_tag": RUNNER_TAG,
            "allow_override_args_in_task": False,
            "allow_override_branch_in_task": False,
            "suppress_success_alerts": True,
        }
        old = [item for item in templates if item.get("name") == name]
        if len(old) > 1:
            raise RuntimeError(f"duplicate Semaphore template: {name}")
        if old:
            current = old[0]
            api._request("PUT", template_url + f"/{current['id']}", {**current, **body})
            template_ids[action] = current["id"]
        else:
            created = api._request("POST", template_url, body)
            template_ids[action] = created["id"]

    result = {
        "project_id": project_id,
        "repository_id": repository["id"],
        "inventory_id": inventory["id"],
        "environment_id": environment["id"],
        "templates": template_ids,
        "git_url": git_url,
        "git_revision": revision,
        "git_branch": pinned_branch,
        "runner_tag": RUNNER_TAG,
        "runtime_root": str(runtime_root),
        "stack_sha256": stack_sha256,
    }
    state_path = STATE_ROOT / "voice-sidecar-project.json"
    state_path.write_text(json.dumps(result, indent=2) + "\n")
    state_path.chmod(0o600)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-root", type=Path, required=True)
    parser.add_argument("--stack-sha256", required=True)
    args = parser.parse_args()
    runtime_root = args.runtime_root.resolve()
    if not runtime_root.name.endswith("-full"):
        raise SystemExit("runtime root must be a reviewed staged v2 artifact")
    stack_path = runtime_root / "impl/deployment/stack.py"
    actual = subprocess.check_output(["shasum", "-a", "256", str(stack_path)], text=True).split()[0]
    if actual != args.stack_sha256:
        raise SystemExit("staged runtime stack digest mismatch")
    git_url, revision = _create_repository()
    result = provision(git_url, revision, runtime_root, args.stack_sha256)
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
