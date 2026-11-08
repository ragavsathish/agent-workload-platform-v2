"""Opt-in named acceptance checks for a supervised local release report."""

import os
import unittest


class LocalReleaseAcceptance(unittest.TestCase):
    def setUp(self):
        from local_release import load_report
        self.report = load_report(os.environ["AWP_LOCAL_RELEASE_REPORT"])

    def require_exercised(self, key):
        result = self.report["journeys"][key]
        self.assertEqual("exercised", result["outcome"], result.get("reason"))
        self.assertTrue(result["evidence"])

    def test_release_001_supervised_readiness(self):
        self.require_exercised("readiness")

    def test_release_002_spoken_turn(self):
        self.require_exercised("voice")

    def test_release_003_task_and_download(self):
        self.require_exercised("task")

    def test_release_004_reviewed_knowledge(self):
        self.require_exercised("knowledge")

    def test_release_005_private_song(self):
        self.require_exercised("song")

    def test_release_006_incident_investigation(self):
        self.require_exercised("incident")

    def test_release_007_restart_without_replay(self):
        self.require_exercised("restart")

    def test_release_008_explicit_worker_recovery(self):
        self.require_exercised("recovery")

    def test_release_009_foreign_owner_denied(self):
        self.require_exercised("isolation")

    def test_release_010_missing_dependency_is_honest(self):
        self.assertTrue(self.report["limitations"])
        self.assertNotIn("exercised", {item["outcome"] for item in self.report["gates"].values()})

    def test_release_011_status_scoped_to_evidence(self):
        self.assertEqual("local", self.report["scope"])
        self.assertEqual({"external_media", "external_image", "private_migration", "model_quality", "pull_request"}, set(self.report["gates"]))


if __name__ == "__main__":
    unittest.main()
