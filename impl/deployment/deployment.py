"""Local service and qualification controls for the v2 owner deployment.

The public identity boundary is the separately configured Tailscale Serve and
Traefik namespace. This module never turns a loopback identity into public proof.
"""
from __future__ import annotations

import ipaddress
import json
import os
import signal
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit


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
                    if response.status == 200:
                        return
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
        if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost"}:
            raise ValueError("Grain scheduler endpoint must be loopback HTTP")
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
            self.client.call("dispatch", {"task-id": task_id, "workspace": task["workspace"]})
            task_env = dict(self.env)
            workspace_map = json.loads(self.env.get("AWP_WORKSPACE_MAP_JSON", "{}"))
            workspace = workspace_map.get(task["workspace"])
            if not isinstance(workspace, str) or not Path(workspace).is_dir():
                self.client.call("interrupt", {"task-id": task_id})
                self.scheduler.interrupt(task_id)
                raise RuntimeError("accepted task workspace is not published on this host")
            task_env.update({"AWP_SCHEDULED_TASK_ID": task_id,
                             "AWP_SCHEDULED_TASK_WORKSPACE": workspace,
                             "AWP_SCHEDULED_TASK_INSTRUCTION": task["instruction"]})
            self.running[task_id] = subprocess.Popen(self.worker_command, env=task_env,
                                                       start_new_session=True,
                                                       stdout=subprocess.DEVNULL,
                                                       stderr=subprocess.DEVNULL)
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
