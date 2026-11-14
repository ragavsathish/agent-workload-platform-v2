"""Local service and qualification controls for the v2 owner deployment.

The public identity boundary is the separately configured Tailscale Serve and
Traefik namespace. This module never turns a loopback identity into public proof.
"""
from __future__ import annotations

import ipaddress
import json
import os
import re
import shutil
import signal
import subprocess
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit


TASK_ID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")


@dataclass(frozen=True)
class PreparedTaskWorkspace:
    path: Path
    source_revision: str
    image_ref: str | None = None
    gitea_repository: str | None = None


def prepare_task_workspace(task_id: str, logical: str, source: Path, task_root: Path,
                           mode: str = "host", image_map: dict[str, str] | None = None) -> PreparedTaskWorkspace:
    """Materialize one immutable selected source into a task-owned working tree."""
    if not TASK_ID.fullmatch(task_id):
        raise ValueError("invalid task identity")
    if logical != "scratch" and not re.fullmatch(r"[a-z0-9][a-z0-9._-]{0,127}", logical):
        raise ValueError("invalid selected repository")
    if mode not in {"host", "gondolin"}:
        raise ValueError("invalid task execution mode")
    destination = task_root / task_id
    marker = destination / "source.json"
    workspace = destination / "workspace"
    if marker.exists():
        record = json.loads(marker.read_text())
        if (record.get("logical") != logical or record.get("source") != str(source.resolve())
                or record.get("mode") != mode
                or not workspace.is_dir()):
            raise ValueError("prepared task source does not match selection")
        image_ref = record.get("image_ref")
        if image_ref and not Path(image_ref).exists():
            raise ValueError("prepared execution image is unavailable")
        return PreparedTaskWorkspace(workspace, record["revision"], record.get("image_ref"),
                                     record.get("gitea_repository"))
    if destination.exists():
        raise ValueError("task workspace is incomplete")
    if logical != "scratch" and not source.is_dir():
        raise ValueError("selected source is unavailable")
    task_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    staging = Path(tempfile.mkdtemp(prefix=f".{task_id}-", dir=task_root))
    try:
        if logical == "scratch":
            (staging / "workspace").mkdir(mode=0o700)
            revision = "scratch"
        else:
            revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"],
                                               text=True, stderr=subprocess.DEVNULL).strip()
            if not re.fullmatch(r"[0-9a-f]{40}", revision):
                raise ValueError("selected source revision is invalid")
            subprocess.run(["git", "clone", "--quiet", "--no-hardlinks", str(source), str(staging / "workspace")],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            copied = subprocess.check_output(["git", "-C", str(staging / "workspace"), "rev-parse", "HEAD"],
                                             text=True).strip()
            if copied != revision:
                raise ValueError("selected source changed during preparation")
        image_ref = None
        gitea_repository = None
        config_file = staging / "workspace" / ".pi" / "gondolin.json"
        if logical != "scratch" and config_file.is_file():
            config = json.loads(config_file.read_text())
            if not isinstance(config, dict):
                raise ValueError("invalid task execution declaration")
            grant = config.get("gitea")
            if grant is not None:
                if not isinstance(grant, dict) or grant.get("access") != "write" or grant.get("repository") != logical:
                    raise ValueError("repository grant does not match selected workspace")
                gitea_repository = logical
            if mode == "gondolin" and "image" in config:
                declared = config["image"]
                if not isinstance(declared, str) or not declared:
                    raise ValueError("declared execution image is invalid")
                mapped = (image_map or {}).get(f"{declared}@{revision}")
                if mapped:
                    candidate = Path(mapped).resolve()
                else:
                    relative = Path(declared)
                    if relative.is_absolute():
                        raise ValueError("declared execution image requires a verified handoff")
                    candidate = (staging / "workspace" / relative).resolve()
                    if not candidate.is_relative_to((staging / "workspace").resolve()):
                        raise ValueError("declared execution image escaped selected source")
                if not candidate.is_file() and not candidate.is_dir():
                    raise ValueError("declared execution image is unavailable")
                image_ref = str(candidate if mapped else (destination / "workspace" / relative))
        _write_json(staging / "source.json", {"logical": logical, "source": str(source.resolve()), "mode": mode,
                                               "revision": revision, "image_ref": image_ref,
                                               "gitea_repository": gitea_repository})
        staging.replace(destination)
        return PreparedTaskWorkspace(workspace, revision, image_ref, gitea_repository)
    finally:
        if staging.exists():
            shutil.rmtree(staging)


def _write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, sort_keys=True))
    tmp.chmod(0o600)
    tmp.replace(path)


def _read_json(path: Path, default: object) -> object:
    return json.loads(path.read_text()) if path.exists() else default


@dataclass(frozen=True)
class OwnerIngress:
    owner: str
    origin: str

    def authorize(self, identity: str, origin: str) -> str:
        expected = urlsplit(self.origin)
        actual = urlsplit(origin)
        if expected.scheme != "https" or not expected.hostname or expected.path not in ("", "/"):
            raise ValueError("configured public origin must be HTTPS")
        if identity != self.owner or actual != expected:
            raise PermissionError("owner entry denied")
        return self.owner


@dataclass(frozen=True)
class MediaProof:
    origin: str
    peer_address: str
    selected_address: str
    candidate_type: str
    input_samples: int
    output_samples: int
    turn_id: str
    answer_hash: str

    def qualifies(self) -> bool:
        parsed = urlsplit(self.origin)
        if parsed.scheme != "https" or not parsed.hostname or not self.turn_id or not self.answer_hash:
            return False
        try:
            peer = ipaddress.ip_address(self.peer_address)
            selected = ipaddress.ip_address(self.selected_address)
        except ValueError:
            return False
        if any(address.is_loopback or address.is_unspecified for address in (peer, selected)):
            return False
        return self.candidate_type in {"host", "srflx", "relay"} and self.input_samples > 0 and self.output_samples > 0


@dataclass(frozen=True)
class ServiceSpec:
    name: str
    command: tuple[str, ...]
    cwd: Path
    ready_url: str | None = None
    ready_headers: dict[str, str] | None = None
    ready_method: str = "GET"
    ready_statuses: tuple[int, ...] = (200,)


class ServiceSupervisor:
    """Own child process groups; a launchd job can keep this supervisor alive."""
    def __init__(self, specs: tuple[ServiceSpec, ...], run_dir: Path, env: dict[str, str] | None = None):
        self.specs = {spec.name: spec for spec in specs}
        self.run_dir = run_dir
        self.env = env or dict(os.environ)
        self.children: dict[str, subprocess.Popen] = {}
        run_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        run_dir.chmod(0o700)

    def start(self) -> None:
        for name, spec in self.specs.items():
            self.start_one(name)
            if spec.ready_url:
                self.wait_ready(spec)

    def wait_ready(self, spec: ServiceSpec, timeout: float = 180.0) -> None:
        import urllib.error
        import urllib.request
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            process = self.children[spec.name]
            if process.poll() is not None:
                raise RuntimeError(f"{spec.name} exited before readiness")
            request = urllib.request.Request(spec.ready_url,
                data=b"{}" if spec.ready_method == "POST" else None,
                headers=spec.ready_headers or {}, method=spec.ready_method)
            try:
                with urllib.request.urlopen(request, timeout=3) as response:
                    if response.status in spec.ready_statuses:
                        return
            except urllib.error.HTTPError as exc:
                if exc.code in spec.ready_statuses:
                    return
                time.sleep(0.5)
            except Exception:
                time.sleep(0.5)
        raise RuntimeError(f"{spec.name} did not become ready")

    def start_one(self, name: str) -> None:
        if name in self.children and self.children[name].poll() is None:
            return
        spec = self.specs[name]
        log = (self.run_dir / f"{name}.log").open("ab", buffering=0)
        process = subprocess.Popen(spec.command, cwd=spec.cwd, env=self.env,
                                   stdout=log, stderr=subprocess.STDOUT,
                                   start_new_session=True, close_fds=True)
        log.close()
        self.children[name] = process
        _write_json(self.run_dir / f"{name}.json", {"pid": process.pid, "command": list(spec.command)})

    def status(self) -> dict[str, dict[str, object]]:
        return {name: {"pid": process.pid, "running": process.poll() is None,
                       "exit_code": process.poll()}
                for name, process in self.children.items()}

    def restart(self, name: str) -> None:
        self.stop_one(name)
        self.start_one(name)

    def stop_one(self, name: str) -> None:
        process = self.children.pop(name, None)
        if process is None:
            return
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=5)
        (self.run_dir / f"{name}.json").unlink(missing_ok=True)

    def stop(self) -> None:
        for name in reversed(tuple(self.specs)):
            self.stop_one(name)

    def supervise(self, interval: float = 2.0) -> None:
        try:
            self.start()
            while True:
                for name in self.specs:
                    if self.children[name].poll() is not None:
                        time.sleep(min(interval, 10))
                        self.start_one(name)
                time.sleep(interval)
        finally:
            self.stop()


class TaskScheduler:
    """Persistent claim ledger. Grain remains authoritative for task transitions."""
    def __init__(self, path: Path):
        self.path = path
        self.claims = _read_json(path, {})

    def claim(self, tasks: list[dict[str, object]]) -> str | None:
        for task in tasks:
            task_id = str(task["task_id"])
            if task.get("status") == "pending" and not task.get("recovery_required") and task_id not in self.claims:
                self.claims[task_id] = {"state": "claimed", "at": time.time()}
                _write_json(self.path, self.claims)
                return task_id
        return None

    def interrupt(self, task_id: str) -> None:
        self.claims[task_id] = {"state": "interrupted", "at": time.time()}
        _write_json(self.path, self.claims)

    def reconsider(self, task_id: str) -> None:
        if self.claims.get(task_id, {}).get("state") == "interrupted":
            self.claims.pop(task_id)
            _write_json(self.path, self.claims)

    def reason(self, task_id: str) -> str | None:
        claim = self.claims.get(task_id)
        return "recovery required" if claim and claim["state"] == "interrupted" else None


class VersionStore:
    def __init__(self, path: Path, current_link: Path | None = None):
        self.path = path
        self.current_link = current_link
        self.state = _read_json(path, {"active": None, "qualified": [], "releases": {}})
        self.state.setdefault("releases", {})

    def register(self, version: str, release: Path) -> None:
        resolved = release.resolve(strict=True)
        if not (resolved / "impl/deployment/stack.py").is_file():
            raise ValueError("release is missing the service manager")
        self.state["releases"][version] = str(resolved)
        _write_json(self.path, self.state)

    def _switch(self, version: str) -> None:
        if self.current_link:
            release = self.state["releases"].get(version)
            if not release:
                raise RuntimeError("version has no registered release")
            self.current_link.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            candidate = self.current_link.with_name(self.current_link.name + ".next")
            candidate.unlink(missing_ok=True)
            candidate.symlink_to(release, target_is_directory=True)
            candidate.replace(self.current_link)

    def qualify(self, version: str) -> None:
        if version not in self.state["qualified"]:
            self.state["qualified"].append(version)
        self._switch(version)
        self.state["active"] = version
        _write_json(self.path, self.state)

    def activate(self, version: str) -> None:
        self._switch(version)
        self.state["active"] = version
        _write_json(self.path, self.state)

    def rollback(self) -> None:
        previous = [version for version in self.state["qualified"] if version != self.state["active"]]
        if not previous:
            raise RuntimeError("no previous qualified version")
        self._switch(previous[-1])
        self.state["active"] = previous[-1]
        _write_json(self.path, self.state)

    def active(self) -> str | None:
        return self.state["active"]


def readiness(services: dict[str, bool]) -> bool:
    return bool(services) and all(services.values())

class GrainTaskClient:
    """Private scheduler capability to the Grain task authority."""
    def __init__(self, base_url: str, token: str, owner: str | None = None):
        parsed = urlsplit(base_url)
        allowed_hosts = {"127.0.0.1", "localhost", "grain.awp-v2.svc.cluster.local"}
        if parsed.scheme != "http" or parsed.hostname not in allowed_hosts:
            raise ValueError("Grain scheduler endpoint must be loopback or the private k3d Grain service")
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.owner = owner

    def call(self, operation: str, body: dict | None = None) -> dict:
        import urllib.request
        request = urllib.request.Request(
            f"{self.base_url}/internal/task/{operation}",
            data=json.dumps(body or {}).encode(),
            headers={"Authorization": f"Bearer {self.token}", "Content-Type": "application/json",
                     **({"X-AWP-Owner": self.owner} if self.owner else {})},
            method="POST")
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.load(response)

    def tasks(self) -> list[dict]:
        return self.call("list")["tasks"]


class ScheduledExecutor:
    """Polls Grain for accepted work; one VM worker at a time by default."""
    def __init__(self, client: GrainTaskClient, scheduler: TaskScheduler,
                 worker_command: tuple[str, ...], env: dict[str, str], capacity: int = 1):
        self.client = client
        self.scheduler = scheduler
        self.worker_command = worker_command
        self.env = env
        self.capacity = capacity
        self.running: dict[str, subprocess.Popen] = {}

    def reconcile(self) -> None:
        tasks = self.client.tasks()
        for task in tasks:
            task_id = str(task.get("task-id", task.get("task_id", "")))
            status = str(task.get("status", ""))
            if status == "running" and task_id not in self.running:
                # A previous process lost its VM; require an explicit owner retry.
                self.client.call("interrupt", {"task-id": task_id})
                self.scheduler.interrupt(task_id)
        for task in tasks:
            task_id = str(task.get("task-id", task.get("task_id", "")))
            if task.get("status") == "pending" and not task.get("recovery-required?", False):
                self.scheduler.reconsider(task_id)
        while len(self.running) < self.capacity:
            normalized = [{"task_id": str(t.get("task-id", t.get("task_id", ""))),
                           "status": str(t.get("status", "")),
                           "recovery_required": bool(t.get("recovery-required?", False))}
                          for t in tasks]
            task_id = self.scheduler.claim(normalized)
            if not task_id:
                break
            task = next(t for t in tasks if str(t.get("task-id", t.get("task_id"))) == task_id)
            task_env = dict(self.env)
            workspace_map = json.loads(self.env.get("AWP_WORKSPACE_MAP_JSON", "{}"))
            workspace = workspace_map.get(task["workspace"])
            task_root = Path(self.env.get("AWP_TASK_WORKSPACE_DIR") or
                             str(Path(self.env.get("AWP_TASKING_STORAGE_DIR") or
                                      self.scheduler.path.parent) / "workspaces"))
            try:
                if not isinstance(workspace, str) or not Path(workspace).is_dir():
                    raise ValueError("selected workspace is unavailable")
                prepared = prepare_task_workspace(task_id, task["workspace"], Path(workspace), task_root,
                                                  self.env.get("WORKER_PI_EXECUTION_MODE", "gondolin"),
                                                  json.loads(self.env.get("AWP_TASK_IMAGE_MAP_JSON", "{}")))
            except (ValueError, OSError, subprocess.CalledProcessError, json.JSONDecodeError):
                self.client.call("fail", {"task-id": task_id,
                                          "failure": "Selected source or execution image preparation failed."})
                self.scheduler.interrupt(task_id)
                tasks = [t for t in tasks if str(t.get("task-id", t.get("task_id"))) != task_id]
                continue
            self.client.call("dispatch", {"task-id": task_id, "workspace": task["workspace"],
                                          "source-revision": prepared.source_revision})
            task_env.update({"AWP_SCHEDULED_TASK_ID": task_id,
                             "AWP_SCHEDULED_TASK_WORKSPACE": str(prepared.path),
                             "AWP_SCHEDULED_TASK_INSTRUCTION": task["instruction"],
                             "AWP_SCHEDULED_TASK_SOURCE_REVISION": prepared.source_revision,
                             "AWP_SCHEDULED_TASK_IMAGE_REF": prepared.image_ref or "",
                             "AWP_SCHEDULED_TASK_GITEA_REPOSITORY": prepared.gitea_repository or ""})
            try:
                log_dir = Path(self.env.get("AWP_TASK_WORKER_LOG_DIR") or str(task_root.parent / "worker-logs"))
                log_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
                log_dir.chmod(0o700)
                descriptor = os.open(log_dir / f"{task_id}.log", os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
                with os.fdopen(descriptor, "ab", buffering=0) as output:
                    self.running[task_id] = subprocess.Popen(self.worker_command, env=task_env,
                                                               start_new_session=True,
                                                               stdout=output, stderr=subprocess.STDOUT)
            except OSError:
                self.client.call("fail", {"task-id": task_id,
                                          "failure": "Task worker could not start; inspect private diagnostics."})
                self.scheduler.interrupt(task_id)
            tasks = [t for t in tasks if str(t.get("task-id", t.get("task_id"))) != task_id]
        for task_id, process in list(self.running.items()):
            if process.poll() is not None:
                self.running.pop(task_id)

    def run(self, interval: float = 3.0) -> None:
        try:
            while True:
                try:
                    self.reconcile()
                except Exception as error:
                    # A failed poll is not permission to replay or abandon work.
                    print(f"scheduler poll unavailable: {type(error).__name__}", flush=True)
                time.sleep(interval)
        finally:
            for task_id, process in self.running.items():
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait(timeout=5)
                    try:
                        self.client.call("interrupt", {"task-id": task_id})
                    finally:
                        self.scheduler.interrupt(task_id)
