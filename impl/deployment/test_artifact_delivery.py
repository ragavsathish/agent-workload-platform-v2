"""Named public-path checks for slice 12 task artifact delivery."""
from __future__ import annotations

import io
import json
import os
import socket
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import unittest
import zipfile
from pathlib import Path

from deployment import GrainTaskClient
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
