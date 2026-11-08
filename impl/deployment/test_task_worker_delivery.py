"""Slice 13 selected-source, worker and owner-delivery acceptance checks."""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from deployment import ScheduledExecutor, TaskScheduler, prepare_task_workspace


class TaskWorkerDeliveryAcceptance(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.source.mkdir()
        subprocess.run(["git", "-C", str(self.source), "init", "-q"], check=True)
        (self.source / "note.txt").write_text("source\n")
        subprocess.run(["git", "-C", str(self.source), "add", "."], check=True)
        subprocess.run(["git", "-C", str(self.source), "-c", "user.name=Test", "-c", "user.email=test@example.test",
                        "commit", "-qm", "source"], check=True)

    def test_delivery_001_002_task_specific_preparation(self):
        first = prepare_task_workspace("11111111-1111-4111-8111-111111111111", "project", self.source, self.root / "tasks")
        second = prepare_task_workspace("22222222-2222-4222-8222-222222222222", "project", self.source, self.root / "tasks")
        self.assertNotEqual(first.path, second.path)
        self.assertEqual((first.path / "note.txt").read_text(), "source\n")
        (first.path / "note.txt").write_text("first task\n")
        self.assertEqual((second.path / "note.txt").read_text(), "source\n")
        self.assertEqual(first.source_revision, second.source_revision)
        self.assertEqual(len(first.source_revision), 40)

    def test_delivery_001_scratch_preparation(self):
        prepared = prepare_task_workspace("33333333-3333-4333-8333-333333333333", "scratch", self.root, self.root / "tasks")
        self.assertTrue(prepared.path.is_dir())
        self.assertEqual(list(prepared.path.iterdir()), [])
        self.assertEqual(prepared.source_revision, "scratch")

    def test_delivery_004_missing_source_fails_before_attempt(self):
        with self.assertRaises(ValueError):
            prepare_task_workspace("44444444-4444-4444-8444-444444444444", "project", self.root / "missing", self.root / "tasks")

    def test_delivery_003_004_declared_image_and_repository_grant(self):
        policy = self.source / ".pi"
        policy.mkdir()
        (policy / "gondolin.json").write_text('{"image":"project-image","gitea":{"access":"write","repository":"project"}}')
        subprocess.run(["git", "-C", str(self.source), "add", "."], check=True)
        subprocess.run(["git", "-C", str(self.source), "-c", "user.name=Test", "-c", "user.email=test@example.test",
                        "commit", "-qm", "image policy"], check=True)
        revision = subprocess.check_output(["git", "-C", str(self.source), "rev-parse", "HEAD"], text=True).strip()
        image = self.root / "installed-image"
        image.mkdir()
        with self.assertRaises(ValueError):
            prepare_task_workspace("55555555-5555-4555-8555-555555555555", "project", self.source,
                                   self.root / "tasks", "gondolin")
        prepared = prepare_task_workspace("66666666-6666-4666-8666-666666666666", "project", self.source,
                                          self.root / "tasks", "gondolin",
                                          {f"project-image@{revision}": str(image)})
        self.assertEqual(prepared.image_ref, str(image.resolve()))
        self.assertEqual(prepared.gitea_repository, "project")
        self.assertEqual(prepared.source_revision, revision)
        image.rmdir()
        with self.assertRaises(ValueError):
            prepare_task_workspace("66666666-6666-4666-8666-666666666666", "project", self.source,
                                   self.root / "tasks", "gondolin",
                                   {f"project-image@{revision}": str(image)})

    def test_delivery_004_unavailable_selected_source_settles_original_task(self):
        task_id = "77777777-7777-4777-8777-777777777777"
        class Grain:
            def __init__(self): self.calls = []
            def tasks(self): return [{"task-id": task_id, "workspace": "project", "instruction": "Work", "status": "pending"}]
            def call(self, operation, body): self.calls.append((operation, body)); return {"status": "failed"}
        grain = Grain()
        executor = ScheduledExecutor(grain, TaskScheduler(self.root / "claims.json"),
                                     ("/bin/true",), {"AWP_WORKSPACE_MAP_JSON": '{"project":"/missing/source"}'})
        executor.reconcile()
        self.assertEqual([operation for operation, _ in grain.calls], ["fail"])
        self.assertEqual(grain.calls[0][1]["task-id"], task_id)
        self.assertEqual(executor.running, {})

    def test_delivery_003_dispatch_hands_image_and_grant_to_worker(self):
        task_id = "99999999-9999-4999-8999-999999999999"
        policy = self.source / ".pi"
        policy.mkdir()
        (policy / "gondolin.json").write_text('{"image":"project-image","gitea":{"access":"write","repository":"project"}}')
        subprocess.run(["git", "-C", str(self.source), "add", "."], check=True)
        subprocess.run(["git", "-C", str(self.source), "-c", "user.name=Test", "-c", "user.email=test@example.test",
                        "commit", "-qm", "image policy"], check=True)
        revision = subprocess.check_output(["git", "-C", str(self.source), "rev-parse", "HEAD"], text=True).strip()
        image = self.root / "installed-image"
        image.mkdir()
        seen = self.root / "handoff.json"
        worker = self.root / "handoff.py"
        worker.write_text("import json,os,pathlib\npathlib.Path(os.environ['SEEN']).write_text(json.dumps({k:os.environ[k] for k in ['AWP_SCHEDULED_TASK_WORKSPACE','AWP_SCHEDULED_TASK_SOURCE_REVISION','AWP_SCHEDULED_TASK_IMAGE_REF','AWP_SCHEDULED_TASK_GITEA_REPOSITORY']}))\n")
        class Grain:
            def __init__(self): self.calls = []
            def tasks(self): return [{"task-id": task_id, "workspace": "project", "instruction": "Work", "status": "pending"}]
            def call(self, operation, body): self.calls.append((operation, body)); return {"status": "running"}
        grain = Grain()
        env = {"AWP_WORKSPACE_MAP_JSON": json.dumps({"project": str(self.source)}),
               "AWP_TASK_IMAGE_MAP_JSON": json.dumps({f"project-image@{revision}": str(image)}),
               "WORKER_PI_EXECUTION_MODE": "gondolin", "SEEN": str(seen)}
        executor = ScheduledExecutor(grain, TaskScheduler(self.root / "claims.json"),
                                     (sys.executable, str(worker)), env)
        executor.reconcile()
        for process in executor.running.values(): process.wait(timeout=5)
        handoff = json.loads(seen.read_text())
        self.assertEqual(handoff["AWP_SCHEDULED_TASK_SOURCE_REVISION"], revision)
        self.assertEqual(handoff["AWP_SCHEDULED_TASK_IMAGE_REF"], str(image.resolve()))
        self.assertEqual(handoff["AWP_SCHEDULED_TASK_GITEA_REPOSITORY"], "project")
        self.assertTrue(Path(handoff["AWP_SCHEDULED_TASK_WORKSPACE"]).is_dir())
        self.assertEqual(grain.calls[0][1]["source-revision"], revision)

    def test_delivery_005_worker_spawn_failure_settles_original_task(self):
        task_id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        class Grain:
            def __init__(self): self.calls = []
            def tasks(self): return [{"task-id": task_id, "workspace": "scratch", "instruction": "Work", "status": "pending"}]
            def call(self, operation, body): self.calls.append((operation, body)); return {"status": "failed"}
        grain = Grain()
        executor = ScheduledExecutor(grain, TaskScheduler(self.root / "claims.json"),
                                     ("/no-such-task-worker",),
                                     {"AWP_WORKSPACE_MAP_JSON": json.dumps({"scratch": str(self.root)}),
                                      "WORKER_PI_EXECUTION_MODE": "host"})
        executor.reconcile()
        self.assertEqual([operation for operation, _ in grain.calls], ["dispatch", "fail"])
        self.assertEqual(grain.calls[-1][1]["task-id"], task_id)
        self.assertEqual(executor.running, {})

    def test_delivery_006_lost_worker_requires_owner_retry(self):
        task_id = "88888888-8888-4888-8888-888888888888"
        class Grain:
            def __init__(self):
                self.calls = []
                self.state = {"task-id": task_id, "workspace": "scratch", "instruction": "Work", "status": "running"}
            def tasks(self): return [self.state]
            def call(self, operation, body): self.calls.append((operation, body)); return {"status": "pending"}
        grain = Grain()
        executor = ScheduledExecutor(grain, TaskScheduler(self.root / "claims.json"),
                                     ("/bin/sleep", "1"),
                                     {"AWP_WORKSPACE_MAP_JSON": '{"scratch":"' + str(self.root) + '"}',
                                      "WORKER_PI_EXECUTION_MODE": "host"})
        executor.reconcile()
        self.assertEqual([operation for operation, _ in grain.calls], ["interrupt"])
        grain.state = {**grain.state, "status": "pending", "recovery-required?": True}
        executor.reconcile()
        self.assertEqual([operation for operation, _ in grain.calls], ["interrupt"])
        grain.state["recovery-required?"] = False
        try:
            executor.reconcile()
            self.assertEqual([operation for operation, _ in grain.calls], ["interrupt", "dispatch"])
            self.assertEqual(len(executor.running), 1)
        finally:
            for process in executor.running.values():
                process.terminate(); process.wait(timeout=5)


if __name__ == "__main__":
    unittest.main()
