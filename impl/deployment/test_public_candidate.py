"""Named slice 22 checks. Real external device evidence is a separate gate."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
import shutil

import public_candidate as candidate


class PublicCandidateAcceptance(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_candidate_001_refuses_occupied_resource(self):
        result = candidate.preflight({"project": "awp-v2-public-candidate-22",
            "ports": [9443, 9744], "state": str(self.root / "candidate")},
            occupied_ports={9443}, occupied_projects={"pipecat-mlx-voice"},
            existing_healthy=True, tailscale_version="1.104.4")
        self.assertFalse(result["ready"])
        self.assertIn("port:9443", result["reasons"])

    def test_candidate_002_keeps_independent_identity_after_restart(self):
        identity = candidate.candidate_identity("awp-v2-public-candidate-22",
            "awp-v2-candidate-22", self.root / "candidate", self.root / "existing")
        self.assertEqual(identity["project"], "awp-v2-public-candidate-22")
        self.assertEqual(identity["state"], str((self.root / "candidate").resolve()))
        self.assertEqual(identity, candidate.candidate_identity("awp-v2-public-candidate-22",
            "awp-v2-candidate-22", self.root / "candidate", self.root / "existing"))

    def test_candidate_003_unverified_entry_stays_unexposed(self):
        result = candidate.exposure_decision("1.102.5", None, None)
        self.assertFalse(result["public_ready"])
        self.assertIn("tailscale-version", result["missing"])
        self.assertIn("candidate-origin", result["missing"])

    def test_candidate_004_exact_owner_entry(self):
        result = candidate.owner_entry("owner@example.test", "owner@example.test",
            "https://candidate.tailnet.ts.net", "https://candidate.tailnet.ts.net",
            "owner-peer.tailnet.ts.net", "candidate.tailnet.ts.net", trusted=True)
        self.assertTrue(result["granted"])
        self.assertEqual(result["peer"], "owner-peer.tailnet.ts.net")

    def test_candidate_005_foreign_spoof_and_identityless_refused(self):
        for actual in ("foreign@example.test", ""):
            result = candidate.adversarial_entry("owner@example.test", actual,
                supplied_owner="owner@example.test", local_marker="1", service_token="secret")
            self.assertFalse(result["granted"])

    def test_candidate_006_complete_public_media_requires_private_match(self):
        result = candidate.spoken_journey("https://candidate.tailnet.ts.net",
            "owner-peer.tailnet.ts.net", "candidate.tailnet.ts.net", "198.51.100.1",
            "100.100.100.1", "relay", 16000, 24000,
            "turn-1", "conversation-1", "a" * 64,
            private_turn="turn-1", private_conversation="conversation-1",
            private_answer_hash="a" * 64)
        self.assertTrue(result["qualified"])
        self.assertFalse(candidate.spoken_journey("https://candidate.tailnet.ts.net",
            "owner-peer.tailnet.ts.net", "candidate.tailnet.ts.net", "198.51.100.1",
            "100.100.100.1", "relay", 16000, 24000,
            "turn-1", "conversation-1", "a" * 64,
            private_turn="wrong", private_conversation="conversation-1",
            private_answer_hash="a" * 64)["qualified"])

    def test_candidate_007_failed_media_is_not_spoken_success(self):
        result = candidate.failed_media(signaling=True, selected_pair=False,
                                        input_samples=0, output_samples=0)
        self.assertEqual(result["status"], "unavailable")
        self.assertFalse(result["completed"])

    def test_candidate_008_restart_preserves_ids(self):
        result = candidate.restart_rehearsal({"turn_id": "turn-1", "task_id": "task-1",
            "state_id": "candidate-state", "turn_count": 1, "task_count": 1},
            {"turn_id": "turn-1", "task_id": "task-1", "state_id": "candidate-state",
             "turn_count": 1, "task_count": 1})
        self.assertTrue(result["preserved"])

    def test_candidate_009_failed_change_rolls_back(self):
        result = candidate.rollback_rehearsal(self.root, "qualified-v1", "broken-v2",
            {"turn_id": "turn-1", "task_id": "task-1"})
        self.assertEqual(result["active"], "qualified-v1")
        self.assertTrue(result["preserved"])

    def test_candidate_010_denial_requires_healthy_owner_pass(self):
        self.assertFalse(candidate.paired_denial(foreign_denied=True,
            owner_passed=False, candidate_healthy=False)["qualified"])
        self.assertTrue(candidate.paired_denial(foreign_denied=True,
            owner_passed=True, candidate_healthy=True)["qualified"])

    def test_candidate_011_missing_external_prerequisites_open(self):
        result = candidate.release_decision(local_ready=True, owner_login=None,
            origin=None, owner_peer=None, foreign_peer=None, media_passed=False,
            v1_healthy=True)
        self.assertEqual(result["status"], "prepared-only")
        self.assertFalse(result["public_qualified"])
        self.assertIn("owner-login", result["missing"])
        self.assertIn("separate-owner-peer", result["missing"])

    def test_rendered_compose_has_project_scoped_state_and_no_host_ports(self):
        if not shutil.which("docker"):
            self.skipTest("Docker is unavailable")
        edge = self.root / "edge.env"
        edge.write_text("EDGE_ALLOWED_USERS=owner@example.org\n")
        shape = candidate.compose_shape("awp-v2-public-candidate-22", "awp-v2-candidate-22",
                                        edge, 9743, 9744)
        self.assertEqual(shape["name"], "awp-v2-public-candidate-22")
        self.assertTrue(all(not service.get("ports") for name, service in shape["services"].items()
                            if name != "gitea"))
        self.assertTrue(all(volume["name"].startswith("awp-v2-public-candidate-22_")
                            for volume in shape["volumes"].values()))
        self.assertEqual(candidate.tailnet_image_version(shape["services"]["tailscale"]["image"]),
                         "1.104.4")

    def test_candidate_dependency_is_project_scoped_and_loopback_only(self):
        if not shutil.which("docker"):
            self.skipTest("Docker is unavailable")
        edge = self.root / "edge.env"
        edge.write_text("EDGE_ALLOWED_USERS=owner@example.org\n")
        shape = candidate.compose_shape("awp-v2-public-candidate-22", "awp-v2-candidate-22",
                                        edge, 9743, 9744)
        gitea = shape["services"]["gitea"]
        self.assertEqual(gitea["ports"][0]["host_ip"], "127.0.0.1")
        self.assertEqual(gitea["ports"][0]["published"], "9750")
        self.assertIn("awp-v2-public-candidate-22_gitea-data",
                      (v["name"] for v in shape["volumes"].values()))

    def test_private_config_refuses_v1_port_and_identity_mismatch(self):
        if not shutil.which("docker"):
            self.skipTest("Docker is unavailable")
        state = self.root / "candidate"
        service_path = self.root / "service.env"
        edge_path = self.root / "edge.env"
        service = {
            "AWP_OWNER_IDENTITY": "owner@example.org",
            "AWP_GRAIN_SESSION_PORT": "9747", "AWP_GRAIN_SESSION_URL": "http://127.0.0.1:9747",
            "PI_VOICE_SERVICE_PORT": "9746", "PI_VOICE_SERVICE_URL": "http://127.0.0.1:9746",
            "INFERENCE_BROKER_PORT": "9743", "AGENT_GATEWAY_PORT": "9744",
            "INFERENCE_BROKER_TOKEN": "candidate-broker", "AGENT_GATEWAY_TOKEN": "candidate-gateway",
            **{key: str(state / key.lower()) for key in (
                "AWP_TASKING_STORAGE_DIR", "AWP_SESSION_STORAGE_DIR", "PI_VOICE_SESSION_DIR",
                "AWP_TASK_WORKSPACE_DIR", "AWP_TASK_WORKER_LOG_DIR")}}
        edge = {"EDGE_ALLOWED_USERS": "owner@example.org",
                "PIPECAT_ALLOWED_ORIGINS": "https://candidate.tailnet.ts.net",
                "INFERENCE_BROKER_TOKEN": "candidate-broker", "AGENT_GATEWAY_TOKEN": "candidate-gateway",
                "AGENT_GATEWAY_BASE_URL": "http://host.docker.internal:9744/v1"}
        def save(path, values):
            path.write_text("".join(f"{key}={value}\n" for key, value in values.items()))
            path.chmod(0o600)
        save(service_path, service); save(edge_path, edge)
        config = {"project": "awp-v2-public-candidate-22", "node": "awp-v2-candidate-22",
                  "state": str(state), "existing_state": str(self.root / "existing"),
                  "service_env": str(service_path), "edge_env": str(edge_path),
                  "origin": "https://candidate.tailnet.ts.net",
                  "ports": {"grain": 9747, "pi": 9746, "broker": 9743, "gateway": 9744}}
        self.assertEqual(candidate.validate_private_config(config)["ports"]["broker"], 9743)
        self.assertIn("isolated-gitea", candidate.native_dependencies(service_path))
        edge["EDGE_ALLOWED_USERS"] = "foreign@example.org"
        save(edge_path, edge)
        with self.assertRaisesRegex(ValueError, "owner"):
            candidate.validate_private_config(config)
        edge["EDGE_ALLOWED_USERS"] = "owner@example.org"
        save(edge_path, edge)
        service["INFERENCE_BROKER_PORT"] = "9443"
        save(service_path, service)
        with self.assertRaisesRegex(ValueError, "port"):
            candidate.validate_private_config(config)


if __name__ == "__main__":
    unittest.main()
