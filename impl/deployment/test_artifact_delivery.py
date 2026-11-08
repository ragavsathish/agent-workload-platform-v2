"""Named public-path checks for slice 12 task artifact delivery."""
from __future__ import annotations

import io
import json
import os
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import unittest
import zipfile
from pathlib import Path

from deployment import GrainTaskClient, ScheduledExecutor, TaskScheduler
from gateway import GatewayServer, task_public

ROOT = Path(__file__).resolve().parents[2]
OWNER = "owner@example.test"


class ArtifactDeliveryAcceptance(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.scratch = self.root / "scratch"
        self.scratch.mkdir()
        self.project = self.root / "project"
        self.project.mkdir()
        subprocess.run(["git", "-C", str(self.project), "init", "-q"], check=True)
        (self.project / "readme.txt").write_text("before\n")
        subprocess.run(["git", "-C", str(self.project), "add", "."], check=True)
        subprocess.run(["git", "-C", str(self.project), "-c", "user.name=Test", "-c", "user.email=test@example.test",
                        "commit", "-qm", "base"], check=True)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            self.port = sock.getsockname()[1]
        self.env = {**os.environ, "AWP_TASKING_STORAGE_DIR": str(self.root / "tasking"),
                    "AWP_SESSION_STORAGE_DIR": str(self.root / "sessions"),
                    "AWP_GRAIN_SESSION_TOKEN": "grain-token", "AWP_GRAIN_SESSION_PORT": str(self.port),
                    "AWP_ELIGIBLE_WORKSPACES": "scratch,project",
                    "AWP_WORKSPACE_MAP_JSON": json.dumps({"scratch": str(self.scratch), "project": str(self.project)})}
        self.start_grain()
        self.gateway = GatewayServer(("127.0.0.1", 0), self.grain, "gateway-token", OWNER)
        self.thread = threading.Thread(target=self.gateway.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.stop_gateway)
        self.base = f"http://127.0.0.1:{self.gateway.server_port}/v1/tasks"

    def start_grain(self):
        self.process = subprocess.Popen(
            ["clojure", "-J--add-opens=java.base/java.nio=ALL-UNNAMED",
             "-J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED", "-M", "-m", "awp-v2.live.main"],
            cwd=ROOT / "impl", env=self.env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.grain = GrainTaskClient(f"http://127.0.0.1:{self.port}", "grain-token", OWNER)
        for _ in range(100):
            try:
                self.grain.tasks()
                return
            except Exception:
                if self.process.poll() is not None:
                    self.fail("Grain exited before task readiness")
                time.sleep(0.1)
        self.fail("Grain did not become ready")

    def stop_grain(self):
        if self.process.poll() is None:
            self.process.terminate()
            self.process.wait(timeout=10)

    def stop_gateway(self):
        self.gateway.shutdown()
        self.gateway.server_close()
        self.thread.join(timeout=5)
        self.stop_grain()

    def request(self, path: str, identity=OWNER, data=None, key=None):
        url = self.base + path + "?" + urllib.parse.urlencode({"identity": identity})
        headers = {"Authorization": "Bearer gateway-token", "Content-Type": "application/json"}
        if key:
            headers["Idempotency-Key"] = key
        request = urllib.request.Request(url, data=json.dumps(data).encode() if data is not None else None,
                                         headers=headers, method="POST" if data is not None else "GET")
        return urllib.request.urlopen(request, timeout=20)

    def submit_and_finish(self, workspace: str, content: str, name: str):
        with self.request("", data={"identity": OWNER, "instruction": "Write work", "workspace": workspace}, key=workspace) as response:
            task = json.load(response)
        task_id = task["task_id"]
        self.grain.call("dispatch", {"task-id": task_id, "workspace": workspace})
        (self.scratch if workspace == "scratch" else self.project).joinpath(name).write_text(content)
        self.assertEqual(self.grain.call("capture-complete", {"task-id": task_id, "response": {"text": "Done"}})["status"], "completed")
        return task_id

    def test_artifact_001_002_005_006_007_009_public_route_and_restart(self):
        project_id = self.submit_and_finish("project", "after\n", "readme.txt")
        scratch_id = self.submit_and_finish("scratch", "hello", "note.txt")
        with self.request(f"/{project_id}") as response:
            view = json.load(response)
        self.assertEqual(view["result"]["result"]["artifacts"], ["patch"])
        self.assertEqual(view["result"]["result"]["downloads"],
                         {"patch": f"/v1/tasks/{project_id}/artifacts/patch"})
        self.assertEqual(view["result"]["result"]["response"]["text"], "Done")
        self.assertNotIn("sha256", json.dumps(view))
        self.assertNotIn(str(self.root), json.dumps(view))
        with self.request(f"/{project_id}/artifacts/patch") as response:
            patch = response.read()
            self.assertIn(b"after", patch)
            self.assertEqual(response.headers["Content-Disposition"], 'attachment; filename="changes.patch"')
            self.assertEqual(response.headers["Cache-Control"], "private, no-store")
            self.assertEqual(response.headers["X-Content-Type-Options"], "nosniff")
        with self.request(f"/{scratch_id}/artifacts/workspace") as response:
            with zipfile.ZipFile(io.BytesIO(response.read())) as archive:
                self.assertEqual(archive.read("note.txt"), b"hello")
            self.assertEqual(response.headers["Content-Type"], "application/zip")
        for path, identity in [(f"/{project_id}/artifacts/workspace", OWNER),
                               (f"/{scratch_id}/artifacts/execution", OWNER),
                               (f"/{project_id}/artifacts/patch", "foreign@example.test")]:
            with self.assertRaises(urllib.error.HTTPError) as denied:
                self.request(path, identity=identity)
            self.assertIn(denied.exception.code, {403, 404})
            denied.exception.close()
        self.stop_grain()
        self.start_grain()
        with self.request(f"/{scratch_id}/artifacts/workspace") as response:
            self.assertEqual(response.headers["Content-Disposition"], 'attachment; filename="workspace.zip"')
            self.assertGreater(len(response.read()), 0)

    def test_artifact_008_missing_object_does_not_change_completion(self):
        task_id = self.submit_and_finish("scratch", "hello", "note.txt")
        (self.root / "tasking" / "artifacts" / task_id / "workspace.zip").unlink()
        with self.assertRaises(urllib.error.HTTPError) as unavailable:
            self.request(f"/{task_id}/artifacts/workspace")
        self.assertEqual(unavailable.exception.code, 404)
        unavailable.exception.close()
        with self.request(f"/{task_id}") as response:
            self.assertEqual(json.load(response)["status"], "completed")

    def test_artifact_005_projection_does_not_publish_worker_fields(self):
        result = task_public({"task-id": "id", "workspace": "scratch", "status": "completed", "result": {
            "response": {"text": "Done", "spoken": "Done", "presentation": "text", "resourceUri": "file:///secret"},
            "artifacts": {"workspace": {"sha256": "private"}, "execution": "s3://private"},
            "rawEvents": ["secret"], "approval_token": "secret"}})
        serialized = json.dumps(result)
        self.assertEqual(result["result"]["result"]["artifacts"], ["workspace"])
        self.assertNotIn("secret", serialized)
        self.assertNotIn("s3://", serialized)

    def run_controlled_scheduled(self, workspace: str, key: str) -> str:
        instruction = "Make one selected change and commit it" if "committed" in key else "Make one selected change"
        with self.request("", data={"identity": OWNER, "instruction": instruction, "workspace": workspace}, key=key) as response:
            task_id = json.load(response)["task_id"]
        worker = self.root / "controlled-worker.py"
        worker.write_text("""import json, os, pathlib, subprocess, urllib.request
task = os.environ['AWP_SCHEDULED_TASK_ID']
root = pathlib.Path(os.environ['AWP_SCHEDULED_TASK_WORKSPACE'])
if os.environ['AWP_SCHEDULED_TASK_SOURCE_REVISION'] == 'scratch':
    (root / 'note.txt').write_text('worker made this\\n')
else:
    (root / 'readme.txt').write_text('worker changed this\\n')
    if 'commit it' in os.environ['AWP_SCHEDULED_TASK_INSTRUCTION']:
        subprocess.run(['git', '-C', str(root), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(root), '-c', 'user.name=Worker', '-c', 'user.email=worker@example.test',
                        'commit', '-qm', 'worker change'], check=True)
for operation, body in [('progress', {'task-id': task, 'summary': 'Writing selected work.'}),
                        ('capture-complete', {'task-id': task, 'response': {'text': 'Worker finished'}})]:
    request = urllib.request.Request(os.environ['AWP_GRAIN_SESSION_URL'] + '/internal/task/' + operation,
        data=json.dumps(body).encode(), method='POST',
        headers={'Authorization': 'Bearer ' + os.environ['AWP_GRAIN_SESSION_TOKEN'],
                 'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=20) as response: response.read()
""")
        env = {**self.env, "AWP_GRAIN_SESSION_URL": self.grain.base_url,
               "WORKER_PI_EXECUTION_MODE": "host"}
        executor = ScheduledExecutor(self.grain, TaskScheduler(self.root / f"{workspace}-claims.json"),
                                     (sys.executable, str(worker)), env)
        try:
            executor.reconcile()
            for _ in range(120):
                task = self.grain.call("read", {"task-id": task_id})
                if task.get("status") in {"completed", "failed"}:
                    break
                time.sleep(0.1)
            self.assertEqual(task["status"], "completed", task)
            self.assertEqual(task["source-revision"], "scratch" if workspace == "scratch" else
                             subprocess.check_output(["git", "-C", str(self.project), "rev-parse", "HEAD"], text=True).strip())
        finally:
            for process in executor.running.values():
                process.wait(timeout=10)
        return task_id

    def test_delivery_001_008_009_scheduled_scratch_owner_download(self):
        """delivery-008 and delivery-009: fresh owner delivery and foreign-owner denial."""
        task_id = self.run_controlled_scheduled("scratch", "delivery-scratch")
        self.assertEqual(list(self.scratch.iterdir()), [])
        self.stop_gateway()
        self.start_grain()
        self.gateway = GatewayServer(("127.0.0.1", 0), self.grain, "gateway-token", OWNER)
        self.thread = threading.Thread(target=self.gateway.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.gateway.server_port}/v1/tasks"
        with self.request(f"/{task_id}") as response:
            view = json.load(response)
        self.assertEqual(view["result"]["result"]["response"]["text"], "Worker finished")
        self.assertEqual(view["source_revision"], "scratch")
        with self.request(f"/{task_id}/artifacts/workspace") as response:
            self.assertEqual(response.headers["Cache-Control"], "private, no-store")
            with zipfile.ZipFile(io.BytesIO(response.read())) as archive:
                self.assertEqual(archive.read("note.txt"), b"worker made this\n")
        with self.assertRaises(urllib.error.HTTPError) as denied:
            self.request(f"/{task_id}/artifacts/workspace", identity="foreign@example.test")
        self.assertEqual(denied.exception.code, 403)
        denied.exception.close()

    def test_delivery_002_scheduled_repository_patch_is_task_specific(self):
        (self.project / "intruder.txt").write_text("not selected\n")
        task_id = self.run_controlled_scheduled("project", "delivery-project")
        with self.request(f"/{task_id}/artifacts/patch") as response:
            patch = response.read()
        self.assertIn(b"worker changed this", patch)
        self.assertNotIn(b"intruder", patch)
        self.assertEqual((self.project / "readme.txt").read_text(), "before\n")

    def test_delivery_002_committed_worker_change_stays_in_patch(self):
        task_id = self.run_controlled_scheduled("project", "delivery-project-committed")
        with self.request(f"/{task_id}/artifacts/patch") as response:
            patch = response.read()
        self.assertIn(b"worker changed this", patch)
        self.assertIn(b"before", patch)

    def test_delivery_005_capture_failure_does_not_advertise_bytes(self):
        with self.request("", data={"identity": OWNER, "instruction": "Oversized work", "workspace": "scratch"}, key="oversized") as response:
            task_id = json.load(response)["task_id"]
        self.grain.call("dispatch", {"task-id": task_id, "workspace": "scratch"})
        (self.scratch / "too-large.bin").write_bytes(b"x" * (16_777_216 + 1))
        self.assertEqual(self.grain.call("capture-complete", {"task-id": task_id,
                                                                "response": {"text": "False success"}})["status"], "failed")
        with self.request(f"/{task_id}") as response:
            view = json.load(response)
        self.assertEqual(view["status"], "failed")
        self.assertIsNone(view["result"]["result"])
        with self.assertRaises(urllib.error.HTTPError) as denied:
            self.request(f"/{task_id}/artifacts/workspace")
        self.assertEqual(denied.exception.code, 404)
        denied.exception.close()

    def test_delivery_007_late_callbacks_preserve_original_artifact(self):
        task_id = self.submit_and_finish("scratch", "first", "note.txt")
        with self.request(f"/{task_id}/artifacts/workspace") as response:
            original = response.read()
        (self.scratch / "note.txt").write_text("second")
        for operation, body in [("capture-complete", {"task-id": task_id, "response": {"text": "Second"}}),
                                ("fail", {"task-id": task_id, "failure": "Late failure"})]:
            with self.assertRaises(urllib.error.HTTPError) as denied:
                self.grain.call(operation, body)
            self.assertEqual(denied.exception.code, 409)
            denied.exception.close()
        with self.request(f"/{task_id}/artifacts/workspace") as response:
            self.assertEqual(response.read(), original)
        with self.request(f"/{task_id}") as response:
            self.assertEqual(json.load(response)["result"]["result"]["response"]["text"], "Done")
