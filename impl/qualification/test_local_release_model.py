"""A local result must not promote health or an unrelated gate into success."""

import json
import tempfile
import unittest
from pathlib import Path

from local_release import gate, load_report, new_report, record, save_report


class LocalReleaseModelTest(unittest.TestCase):
    def setUp(self):
        self.report = new_report(version="candidate", source_revision="a" * 40,
                                 state_identity="private-test-state", owner="owner@example.test")

    def test_journey_needs_its_own_evidence(self):
        with self.assertRaises(ValueError):
            record(self.report, "voice", "exercised")
        record(self.report, "readiness", "exercised", evidence={"services": 5})
        self.assertEqual("unqualified", self.report["journeys"]["voice"]["outcome"])

    def test_missing_dependency_keeps_reason(self):
        with self.assertRaises(ValueError):
            record(self.report, "song", "unavailable")
        record(self.report, "song", "unavailable", reason="lyric worker absent")
        self.assertEqual("lyric worker absent", self.report["journeys"]["song"]["reason"])

    def test_local_result_cannot_qualify_external_gate(self):
        record(self.report, "voice", "exercised", evidence={"turn_id": "turn-1"})
        with self.assertRaises(ValueError):
            gate(self.report, "external_media", "qualified")
        self.assertEqual("open", self.report["gates"]["external_media"]["outcome"])

    def test_report_is_private_and_rejects_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            save_report(path, self.report)
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            self.assertEqual(self.report["state_identity"], load_report(path)["state_identity"])
            self.report["journeys"]["task"]["evidence"] = {"Authorization": "Bearer hidden"}
            with self.assertRaises(ValueError):
                save_report(path, self.report)
            self.assertEqual("unqualified", json.loads(path.read_text())["journeys"]["task"]["outcome"])


if __name__ == "__main__":
    unittest.main()
