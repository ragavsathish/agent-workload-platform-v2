"""Opt-in named acceptance checks for a supervised local release report."""

import os
import hashlib
import json
import sqlite3
import unittest
from pathlib import Path


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
        evidence = self.report["journeys"]["song"]["evidence"]
        for key in ("generation_id", "lyric_task_id", "lyric_result_digest", "manifest_digest",
                    "decision_id", "render_task_id", "audio_sha256"):
            self.assertTrue(evidence.get(key), key)
        self.assertEqual(1, evidence["lyric_attempt"])
        self.assertEqual(200, evidence["owner_playback_status"])
        self.assertEqual(403, evidence["foreign_audio_status"])
        self.assertGreater(evidence["audio_duration_seconds"], 25)
        self.assertGreater(evidence["audio_mean_db"], -50)
        state = Path(os.environ["AWP_LOCAL_RELEASE_REPORT"]).parent
        with sqlite3.connect(state / "lyric-owner.sqlite") as db:
            row = db.execute("SELECT state, attempt, result_json FROM lyric_tasks WHERE task_id=?",
                             (evidence["lyric_task_id"],)).fetchone()
        self.assertIsNotNone(row)
        self.assertEqual(("completed", 1), row[:2])
        result = json.loads(row[2])
        self.assertEqual(evidence["generation_id"], result["generation_id"])
        self.assertEqual(evidence["lyric_task_id"], result["task_id"])
        self.assertEqual(self.report["owner_digest"], hashlib.sha256(result["identity"].encode()).hexdigest())
        audio = (state / "song-audio.m4a").read_bytes()
        self.assertEqual(evidence["audio_bytes"], len(audio))
        self.assertEqual(evidence["audio_sha256"], hashlib.sha256(audio).hexdigest())

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
