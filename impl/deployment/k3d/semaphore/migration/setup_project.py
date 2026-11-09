#!/usr/bin/env python3
"""Provision an isolated, local-only Semaphore project for Grain migration."""
from __future__ import annotations

import http.cookiejar
import json
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
PROJECT_SOURCES = Path(__file__).resolve().parent / "project"
STATE_ROOT = Path.home() / "Library/Application Support/AWPv2/k3d/semaphore"
PROJECT_NAME = "AWP v2 Grain migration"
REPOSITORY_NAME = "awp-v2-grain-migration-playbooks"
INVENTORY_NAME = "macos-host-local"
ENVIRONMENT_NAME = "awp-v2-grain-migration"
VIEW_NAME = "Grain state migration"
NOOP_NAME = "Grain migration project pin check"
PREFLIGHT_NAME = "Grain migration read-only preflight"
CLEANUP_NAME = "Grain migration orphaned task cleanup"
CANDIDATE_PROBE_NAME = "Grain candidate owner projection check"
CANDIDATE_LAYOUT_NAME = "Grain candidate layout read-only inspection"
OWNER_SECRET_NAME = "Grain candidate owner Secret update"
MIGRATION_NAME = "Grain source state migration and restore"
LIFECYCLE_NAME = "Grain native rollback start validation"
SYNTAX_NAME = "Grain migration playbook syntax check"
REFRESH_NAME = "Grain native runtime refresh with rollback"
SECRET_CHECK_NAME = "Grain owner and token input check"
SERVER = "http://localhost:9780"
RUNNER_TAG = "macos-host"
SERVICE_ENV = Path("/private/tmp/awp-v2-deploy-config/service.env")
STATE_DIR = Path("/private/tmp/awp-v2-state")
MIGRATION_BACKUP = Path.home() / "Library/Application Support/AWPv2/migrations/grain-state-slice44-2026-11-09"


def _git(*args: str, cwd: Path) -> str:
    return subprocess.check_output(["git", *args], cwd=cwd, text=True).strip()


def _create_repository() -> tuple[str, str]:
    repo_root = STATE_ROOT / "grain-migration-playbooks.git"
    STATE_ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    STATE_ROOT.chmod(0o700)
    work_parent = Path(tempfile.mkdtemp(prefix="awp-v2-grain-migration-repo-", dir="/private/tmp"))
    work = work_parent / "repo"
    try:
        if repo_root.exists():
            _git("clone", str(repo_root), str(work), cwd=work_parent)
        else:
            work.mkdir(mode=0o700)
            _git("init", "-b", "main", cwd=work)
            _git("config", "user.name", "AWP v2 Operations", cwd=work)
            _git("config", "user.email", "awp-v2-ops@localhost", cwd=work)
        for name in ("noop.yml", "preflight.yml", "cleanup_orphan.yml", "candidate_owner.yml", "candidate_layout.yml",
                     "host_runtime_probe.yml", "host_runtime_probe.py", "host_broker_probe.yml", "host_broker_probe.py",
                     "owner_secret.yml", "migrate.yml", "lifecycle.yml", "syntax.yml", "refresh.yml", "secret_check.yml", "grain-rollback.yaml", "preflight.py",
                     "cleanup_orphan.py", "candidate_probe.py", "candidate_layout.py", "owner_secret.py",
                     "lifecycle.py", "migrate_state.py", "grain.yaml"):
            if name.endswith(".py"):
                source = Path(__file__).resolve().parent / name
            elif name == "grain.yaml":
                source = ROOT / "impl/deployment/k3d/grain.yaml"
            else:
                source = PROJECT_SOURCES / name
            shutil.copy2(source, work / name)
        files = ["noop.yml", "preflight.yml", "cleanup_orphan.yml", "candidate_owner.yml", "candidate_layout.yml",
                 "host_runtime_probe.yml", "host_runtime_probe.py", "host_broker_probe.yml", "host_broker_probe.py",
                 "owner_secret.yml", "migrate.yml", "lifecycle.yml", "syntax.yml", "refresh.yml", "secret_check.yml", "preflight.py",
                 "cleanup_orphan.py", "candidate_probe.py", "candidate_layout.py", "owner_secret.py",
                 "lifecycle.py", "migrate_state.py", "grain.yaml", "grain-rollback.yaml"]
        _git("add", *files, cwd=work)
        if subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=work).returncode != 0:
            _git("commit", "-m", "Slice 44 Semaphore Grain preflight and task cleanup", cwd=work)
        revision = _git("rev-parse", "HEAD", cwd=work)
        if repo_root.exists():
            _git("push", "origin", "main", cwd=work)
        else:
            _git("clone", "--bare", str(work), str(repo_root), cwd=work_parent)
        _git("--git-dir", str(repo_root), "update-ref", f"refs/heads/pin-{revision}", revision, cwd=STATE_ROOT)
        os.chmod(repo_root, 0o700)
        url = repo_root.as_uri()
        return url, revision
    except Exception:
        shutil.rmtree(repo_root, ignore_errors=True)
        raise
    finally:
        shutil.rmtree(work_parent, ignore_errors=True)


class Semaphore:
    def __init__(self) -> None:
        credential_path = STATE_ROOT / "admin.env"
        self.credentials = {}
        for line in credential_path.read_text().splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                self.credentials[key.strip()] = value.strip().strip('"')
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self._request("POST", "/api/auth/login", {
            "auth": self.credentials["username"],
            "password": self.credentials["password"],
        }, expected=(200, 204))

    def _request(self, method: str, path: str, payload=None, expected=(200, 201, 204)):
        data = None if payload is None else json.dumps(payload).encode()
        req = urllib.request.Request(SERVER + path, data=data, method=method,
                                    headers={"Content-Type": "application/json",
                                             "Accept": "application/json"})
        try:
            with self.opener.open(req, timeout=10) as response:
                if response.status not in expected:
                    raise RuntimeError(f"Semaphore API returned HTTP {response.status}")
                body = response.read()
                return json.loads(body) if body else None
        except urllib.error.HTTPError as exc:
            raise RuntimeError(f"Semaphore API returned HTTP {exc.code} for {method} {path}") from None

    def _find_or_create(self, method: str, collection: str, name: str, payload: dict):
        items = self._request("GET", collection + "?sort=name&order=asc") or []
        found = [item for item in items if item.get("name") == name]
        if len(found) > 1:
            raise RuntimeError(f"duplicate Semaphore resource name: {name}")
        if found:
            return found[0]
        return self._request("POST", collection, payload)

    def provision(self, git_url: str, revision: str, stack_sha256: str,
                  runtime_root: Path, source_runtime_root: Path, source_stack_sha256: str) -> dict:
        projects = self._request("GET", "/api/projects") or []
        found = [item for item in projects if item.get("name") == PROJECT_NAME]
        if len(found) > 1:
            raise RuntimeError("duplicate Grain migration projects exist")
        project = found[0] if found else self._request("POST", "/api/projects", {
            "name": PROJECT_NAME, "alert": False, "max_parallel_tasks": 1, "demo": False,
        })
        project_id = project["id"]
        prefix = f"/api/project/{project_id}"
        pinned_branch = f"pin-{revision}"

        repository = self._find_or_create("POST", prefix + "/repositories", REPOSITORY_NAME, {
            "project_id": project_id, "name": REPOSITORY_NAME,
            "git_url": git_url, "git_branch": pinned_branch, "ssh_key_id": 1,
        })
        if repository.get("git_url") != git_url:
            raise RuntimeError("existing Semaphore repository URL does not match the pinned local repository")
        if repository.get("git_branch") != pinned_branch:
            self._request("PUT", prefix + f"/repositories/{repository['id']}", {
                **repository, "git_branch": pinned_branch,
            })
            repository = self._request("GET", prefix + f"/repositories/{repository['id']}")

        inventory_text = """all:\n  hosts:\n    localhost:\n      ansible_connection: local\n"""
        inventory = self._find_or_create("POST", prefix + "/inventory", INVENTORY_NAME, {
            "project_id": project_id, "name": INVENTORY_NAME,
            "inventory": inventory_text, "type": "static-yaml",
            "runner_tag": RUNNER_TAG,
        })

        env_json = json.dumps({
            "awp_repo_root": str(ROOT),
            "awp_state_root": str(STATE_DIR),
            "awp_service_env": str(SERVICE_ENV),
            "awp_stack_sha256": stack_sha256,
            "awp_runtime_root": str(runtime_root),
            "awp_source_runtime_root": str(source_runtime_root),
            "awp_source_stack_sha256": source_stack_sha256,
            "awp_migration_backup": str(MIGRATION_BACKUP),
            "awp_candidate_root": str(Path.home() / "Library/Application Support/AWPv2/k3d/storage/awp-v2-grain-candidate/grain-candidate"),
        }, sort_keys=True)
        environment = self._find_or_create("POST", prefix + "/environment", ENVIRONMENT_NAME, {
            "project_id": project_id, "name": ENVIRONMENT_NAME,
            "json": env_json, "env": "{}",
        })
        if environment.get("json") != env_json:
            self._request("PUT", prefix + f"/environment/{environment['id']}", {
                **environment, "json": env_json,
            })
            environment = self._request("GET", prefix + f"/environment/{environment['id']}")
        if environment.get("json") != env_json:
            raise RuntimeError("Semaphore migration environment does not match the reviewed paths")

        views = self._request("GET", prefix + "/views") or []
        view_found = [item for item in views if item.get("title") == VIEW_NAME or item.get("name") == VIEW_NAME]
        if len(view_found) > 1:
            raise RuntimeError("duplicate Grain migration views exist")
        if view_found:
            view = view_found[0]
        else:
            default_views = [item for item in views if item.get("title") == "All" or item.get("name") == "All"]
            if not default_views:
                raise RuntimeError("Semaphore project has no view for the migration templates")
            view = default_views[0]

        template_url = prefix + "/templates"
        templates = self._request("GET", template_url + "?sort=name&order=asc") or []
        template_ids = {}
        for name, playbook in ((NOOP_NAME, "noop.yml"), (PREFLIGHT_NAME, "preflight.yml"),
                               ("AWP v2 host runtime read-only probe", "host_runtime_probe.yml"),
                               ("AWP v2 broker read-only health status", "host_broker_probe.yml"),
                               (CLEANUP_NAME, "cleanup_orphan.yml"),
                               (CANDIDATE_PROBE_NAME, "candidate_owner.yml"),
                               (CANDIDATE_LAYOUT_NAME, "candidate_layout.yml"),
                               (OWNER_SECRET_NAME, "owner_secret.yml"),
                               (MIGRATION_NAME, "migrate.yml"),
                               (LIFECYCLE_NAME, "lifecycle.yml"),
                               (SYNTAX_NAME, "syntax.yml"),
                               (REFRESH_NAME, "refresh.yml"),
                               (SECRET_CHECK_NAME, "secret_check.yml")):
            matching = [item for item in templates if item.get("name") == name]
            if len(matching) > 1:
                raise RuntimeError(f"duplicate Semaphore template name: {name}")
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
            if matching:
                old = matching[0]
                if (old.get("repository_id") != repository["id"]
                        or old.get("inventory_id") != inventory["id"]
                        or old.get("runner_tag") != RUNNER_TAG
                        or old.get("playbook") != playbook):
                    raise RuntimeError(f"existing template {name} is not pinned as expected")
                if old.get("git_branch") != pinned_branch:
                    self._request("PUT", template_url + f"/{old['id']}", {
                        **old, "git_branch": pinned_branch,
                    })
                    old = self._request("GET", template_url + f"/{old['id']}")
                template_ids[name] = old["id"]
            else:
                created = self._request("POST", template_url, body)
                template_ids[name] = created["id"]
        return {
            "project_id": project_id,
            "repository_id": repository["id"],
            "inventory_id": inventory["id"],
            "environment_id": environment["id"],
            "view_id": view["id"],
            "templates": template_ids,
            "git_url": git_url,
            "git_revision": revision,
            "git_branch": pinned_branch,
            "runner_tag": RUNNER_TAG,
        }


def main() -> None:
    if len(sys.argv) > 1:
        if sys.argv[1:] in (['-h'], ['--help']):
            print("Provision and pin the AWP v2 Semaphore migration project. This script changes local Semaphore state.")
            print(f"Usage: {Path(sys.argv[0]).name}")
            return
        print(f"Usage: {Path(sys.argv[0]).name} [-h|--help]", file=sys.stderr)
        raise SystemExit(2)
    git_url, revision = _create_repository()
    stack_sha256 = subprocess.check_output(
        ["shasum", "-a", "256", str(ROOT / "impl/deployment/stack.py")], text=True
    ).split()[0]
    runtime_root = STATE_ROOT / f"source-{stack_sha256}-full"
    runtime_impl = runtime_root / "impl"
    if not runtime_impl.exists():
        staging_root = STATE_ROOT / f"source-{stack_sha256}-staging"
        if staging_root.exists():
            shutil.rmtree(staging_root)
        staging_root.mkdir(mode=0o700)
        subprocess.run(["cp", "-cR", str(ROOT / "impl"), str(staging_root)], check=True)
        if not (staging_root / "impl/runtime").is_dir():
            raise RuntimeError("APFS runtime clone is incomplete")
        os.replace(staging_root, runtime_root)
    snapshot_hash = subprocess.check_output(
        ["shasum", "-a", "256", str(runtime_impl / "deployment/stack.py")], text=True
    ).split()[0]
    if snapshot_hash != stack_sha256:
        raise RuntimeError("Application Support runtime snapshot does not match the reviewed stack SHA256")
    state_path = STATE_ROOT / "migration-project.json"
    if not state_path.is_file():
        raise RuntimeError("previous Semaphore pin is required to identify the active native runtime")
    previous = json.loads(state_path.read_text())
    source_stack_sha256 = previous.get("stack_sha256")
    if not isinstance(source_stack_sha256, str) or len(source_stack_sha256) != 64:
        raise RuntimeError("previous Semaphore pin has no valid native runtime digest")
    source_runtime_root = STATE_ROOT / f"source-{source_stack_sha256}-full"
    source_stack_path = source_runtime_root / "impl/deployment/stack.py"
    actual_source_sha = subprocess.check_output(["shasum", "-a", "256", str(source_stack_path)], text=True).split()[0]
    if actual_source_sha != source_stack_sha256:
        raise RuntimeError("previous native runtime image does not match its recorded SHA256")
    result = Semaphore().provision(git_url, revision, stack_sha256, runtime_root,
                                   source_runtime_root, source_stack_sha256)
    result["stack_sha256"] = stack_sha256
    state_path.write_text(json.dumps(result, indent=2) + "\n")
    state_path.chmod(0o600)
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
