"""Named slice 06 acceptance checks for the deployable control plane.

External media assertions are run by qualify_public.py against an actual tailnet peer.
"""
import json
import os
import socket
import subprocess
import tempfile
import threading
import time
import urllib.request
import unittest
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from deployment import (OwnerIngress, MediaProof, ServiceSpec, ServiceSupervisor,
                        GrainTaskClient, TaskScheduler, ScheduledExecutor, VersionStore, readiness)
from gateway import GatewayServer


class DeploymentAcceptance(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def test_deploy_001_exact_owner_public_entry(self):
        ingress = OwnerIngress("owner@example.test", "https://voice.tailnet.test")
        self.assertEqual(ingress.authorize("owner@example.test", "https://voice.tailnet.test"), "owner@example.test")

    def test_deploy_002_spoofed_and_foreign_caller(self):
        ingress = OwnerIngress("owner@example.test", "https://voice.tailnet.test")
        for identity in ("foreign@example.test", "", "Owner@Example.Test"):
            with self.assertRaises(PermissionError):
                ingress.authorize(identity, "https://voice.tailnet.test")
        with self.assertRaises(PermissionError):
            ingress.authorize("owner@example.test", "http://127.0.0.1:8080")

    def test_deploy_003_external_media_proof(self):
        proof = MediaProof("https://voice.tailnet.test", "192.0.2.10", "198.51.100.20",
                           "relay", 16000, 24000, "turn-1", "answer-hash")
        self.assertTrue(proof.qualifies())

    def test_deploy_004_signaling_without_media(self):
        proof = MediaProof("https://voice.tailnet.test", "127.0.0.1", "127.0.0.1",
                           "host", 0, 0, "", "")
        self.assertFalse(proof.qualifies())

    def test_deploy_005_restart_preserves_state(self):
        spec = ServiceSpec("grain", ("/bin/sh", "-c", "sleep 30"), self.root)
        sup = ServiceSupervisor((spec,), self.root / "run")
        sup.start()
        first = sup.status()["grain"]["pid"]
        sup.restart("grain")
        self.assertNotEqual(first, sup.status()["grain"]["pid"])
        sup.stop()

    def test_deploy_006_independent_scheduler(self):
        class Grain:
            def __init__(self): self.calls = []
            def tasks(self): return [{"task-id": "t1", "status": "pending", "workspace": "scratch", "instruction": "test"}]
            def call(self, op, data): self.calls.append((op, data)); return {"status": "running"}
        grain = Grain()
        executor = ScheduledExecutor(grain, TaskScheduler(self.root / "claims.json"),
            ("/bin/sh", "-c", "sleep 30"),
            {"AWP_WORKSPACE_MAP_JSON": '{"scratch":"' + str(self.root) + '"}'})
        try:
            executor.reconcile()
            self.assertEqual([op for op, _ in grain.calls], ["dispatch"])
            self.assertIn("t1", executor.running)
            executor.reconcile()
            self.assertEqual([op for op, _ in grain.calls], ["dispatch"])
        finally:
            for process in executor.running.values():
                process.terminate(); process.wait(timeout=5)


    def test_deploy_006_grain_gateway_admits_once(self):
        root = Path(__file__).resolve().parents[2]
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            grain_port = sock.getsockname()[1]
        env = {**os.environ,
               "AWP_TASKING_STORAGE_DIR": str(self.root / "tasking"),
               "AWP_SESSION_STORAGE_DIR": str(self.root / "sessions"),
               "AWP_GRAIN_SESSION_TOKEN": "test-grain-token",
               "AWP_GRAIN_SESSION_PORT": str(grain_port),
               "AWP_ELIGIBLE_WORKSPACES": "scratch"}
        process = subprocess.Popen(
            ["clojure", "-J--add-opens=java.base/java.nio=ALL-UNNAMED",
             "-J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED", "-M", "-m", "awp-v2.live.main"],
            cwd=root / "impl", env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.addCleanup(lambda: (process.terminate(), process.wait(timeout=10)))
        grain = GrainTaskClient(f"http://127.0.0.1:{grain_port}", "test-grain-token", "owner@example.test")
        for _ in range(80):
            try:
                grain.tasks()
                break
            except Exception:
                if process.poll() is not None:
                    self.fail("Grain exited before task gateway was ready")
                time.sleep(0.1)
        else:
            self.fail("Grain task gateway was not ready")
        gateway = GatewayServer(("127.0.0.1", 0), grain, "test-gateway-token", "owner@example.test")
        server_thread = threading.Thread(target=gateway.serve_forever, daemon=True)
        server_thread.start()
        self.addCleanup(lambda: (gateway.shutdown(), gateway.server_close(), server_thread.join()))
        url = f"http://127.0.0.1:{gateway.server_port}/v1/tasks"
        def submit(identity):
            request = urllib.request.Request(url, method="POST",
                data=json.dumps({"identity": identity, "instruction": "Inspect scratch", "workspace": "scratch"}).encode(),
                headers={"Authorization": "Bearer test-gateway-token", "Content-Type": "application/json",
                         "Idempotency-Key": "one-owner-request"})
            with urllib.request.urlopen(request, timeout=10) as response:
                return json.load(response)
        first = submit("owner@example.test")
        second = submit("owner@example.test")
        self.assertEqual(first["task_id"], second["task_id"])
        self.assertEqual(len(grain.tasks()), 1)
        with self.assertRaises(urllib.error.HTTPError) as denied:
            submit("foreign@example.test")
        self.assertEqual(denied.exception.code, 403)
        denied.exception.close()


    def test_knowledge_owner_route_forwards_review_and_refuses_foreign(self):
        seen = []
        class InternalHandler(BaseHTTPRequestHandler):
            def log_message(self, *args): pass
            def do_POST(self):
                size = int(self.headers.get("Content-Length", "0"))
                seen.append((self.path, json.loads(self.rfile.read(size)),
                             self.headers.get("X-AWP-Owner"), self.headers.get("Authorization")))
                value = json.dumps({"status": "accepted", "revision": 1}).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(value)))
                self.end_headers()
                self.wfile.write(value)
        internal = ThreadingHTTPServer(("127.0.0.1", 0), InternalHandler)
        internal_thread = threading.Thread(target=internal.serve_forever, daemon=True)
        internal_thread.start()
        self.addCleanup(lambda: (internal.shutdown(), internal.server_close(), internal_thread.join()))
        grain = GrainTaskClient(f"http://127.0.0.1:{internal.server_port}", "inner-token", "owner@example.test")
        gateway = GatewayServer(("127.0.0.1", 0), grain, "outer-token", "owner@example.test")
        gateway_thread = threading.Thread(target=gateway.serve_forever, daemon=True)
        gateway_thread.start()
        self.addCleanup(lambda: (gateway.shutdown(), gateway.server_close(), gateway_thread.join()))
        url = f"http://127.0.0.1:{gateway.server_port}/v1/knowledge/items/item-1/review"
        def review(identity):
            request = urllib.request.Request(url, method="POST", data=json.dumps({
                "identity": identity, "actionId": "review-1", "expectedRevision": 0,
                "decision": "accept"}).encode(), headers={"Authorization": "Bearer outer-token",
                                                    "Content-Type": "application/json"})
            return urllib.request.urlopen(request, timeout=10)
        with review("owner@example.test") as response:
            self.assertEqual(json.load(response)["status"], "accepted")
        self.assertEqual(seen[0], ("/internal/knowledge/review",
                                   {"item-id": "item-1", "action-id": "review-1",
                                    "expected-revision": 0, "decision": "accept", "text": None},
                                   "owner@example.test", "Bearer inner-token"))
        with self.assertRaises(urllib.error.HTTPError) as denied:
            review("foreign@example.test")
        self.assertEqual(denied.exception.code, 403)
        denied.exception.close()
        self.assertEqual(len(seen), 1)

    def test_deploy_007_interrupted_claim_recovery(self):
        class Grain:
            def __init__(self):
                self.calls = []
                self.task = {"task-id": "t1", "status": "running", "workspace": "scratch", "instruction": "test"}
            def tasks(self): return [self.task]
            def call(self, op, data): self.calls.append((op, data)); return {"status": "pending"}
        grain = Grain()
        scheduler = TaskScheduler(self.root / "claims.json")
        executor = ScheduledExecutor(grain, scheduler, ("/bin/sh", "-c", "sleep 30"),
            {"AWP_WORKSPACE_MAP_JSON": '{"scratch":"' + str(self.root) + '"}'})
        executor.reconcile()
        self.assertEqual([op for op, _ in grain.calls], ["interrupt"])
        self.assertEqual(scheduler.reason("t1"), "recovery required")
        grain.task = {**grain.task, "status": "pending", "recovery-required?": True}
        executor.reconcile()
        self.assertEqual([op for op, _ in grain.calls], ["interrupt"])
        grain.task["recovery-required?"] = False  # owner explicitly retried through Grain
        try:
            executor.reconcile()
            self.assertEqual([op for op, _ in grain.calls], ["interrupt", "dispatch"])
        finally:
            for process in executor.running.values():
                process.terminate(); process.wait(timeout=5)

    def test_deploy_008_rollback_retains_state(self):
        state = self.root / "durable-state"
        state.mkdir()
        (state / "turn.txt").write_text("turn-1")
        for name in ("v1", "v2"):
            (self.root / name / "impl/deployment").mkdir(parents=True)
            (self.root / name / "impl/deployment/stack.py").write_text("release")
        link = self.root / "current"
        versions = VersionStore(state / "versions.json", link)
        versions.register("v1", self.root / "v1")
        versions.register("v2", self.root / "v2")
        versions.qualify("v1")
        versions.activate("v2")
        self.assertEqual(link.resolve(), (self.root / "v2").resolve())
        versions.rollback()
        self.assertEqual(versions.active(), "v1")
        self.assertEqual(link.resolve(), (self.root / "v1").resolve())
        self.assertEqual((state / "turn.txt").read_text(), "turn-1")

    def test_deploy_009_unready_dependency(self):
        self.assertFalse(readiness({"grain": True, "pi": False, "broker": True, "edge": True}))
        self.assertTrue(readiness({"grain": True, "pi": True, "broker": True, "edge": True}))


if __name__ == "__main__":
    unittest.main()
