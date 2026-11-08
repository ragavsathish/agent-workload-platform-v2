"""Slice 18 named acceptance scenarios with synthetic RDF and isolated Grain state."""
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

import continuity


def cell(value, kind="uri"):
    return {"type": kind, "value": value}


def quad(graph, subject, predicate, obj, kind="uri"):
    return {"g": cell(graph), "s": cell(subject), "p": cell(predicate), "o": cell(obj, kind)}


GRAPH = "urn:test:journal"
TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"


def resource(legacy_id, kind, subject, value, source="synthetic:turn"):
    return {"legacy-id": legacy_id, "kind": kind, "owner": "synthetic-owner", "source-id": source,
            "value": {"graph": GRAPH, "subject": subject, "sourceRefs": [source], **value}}


def package():
    journal = resource("journal-1", "journal", "urn:test:entry", {
        "text": "Synthetic accepted knowledge", "status": "accepted", "quads": [
            quad(GRAPH, "urn:test:entry", TYPE, "https://schema.org/CreativeWork"),
            quad(GRAPH, "urn:test:entry", "https://schema.org/additionalType", "urn:test:learning"),
            quad(GRAPH, "urn:test:entry", "https://schema.org/text", "Synthetic accepted knowledge", "literal")],
    })
    review = resource("review-1", "review-action", "urn:test:review", {
        "target": "urn:test:entry", "action": "corrected", "createdAt": "2026-01-01T00:00:00Z", "quads": [
            quad(GRAPH, "urn:test:review", TYPE, "https://schema.org/ReviewAction")],
    })
    review_later = resource("review-2", "review-action", "urn:test:review-later", {
        "target": "urn:test:entry", "action": "accepted", "createdAt": "2026-01-02T00:00:00Z", "quads": [
            quad(GRAPH, "urn:test:review-later", TYPE, "https://schema.org/ReviewAction")],
    })
    daily = resource("daily-1", "daily", "urn:test:daily", {
        "text": "Synthetic day", "quads": [
            quad(GRAPH, "urn:test:daily", TYPE, "urn:test:DailyJournal")],
    })
    return {"snapshot-id": "synthetic-snapshot", "digest": "synthetic-digest",
            "owner": "synthetic-owner", "quarantine-count": 0,
            "resources": [journal, review_later, daily, review]}


class ContinuityAcceptance(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.room = tempfile.TemporaryDirectory(prefix="awp-continuity-synthetic-")
        cls.root = Path(cls.room.name)
        cls.package = package()
        input_file = cls.root / "package.json"
        input_file.write_text(json.dumps(cls.package))
        output_file = cls.root / "grain.json"
        command = ["clojure", "-J--add-opens=java.base/java.nio=ALL-UNNAMED",
                   "-J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
                   "-M", "-m", "awp-v2.knowledge.continuity-tool",
                   str(cls.root / "grain-store"), str(input_file), str(output_file)]
        result = subprocess.run(command, cwd=continuity.REPO / "impl", capture_output=True, text=True,
                                timeout=120)
        if result.returncode:
            raise AssertionError(f"synthetic Grain rehearsal failed: {result.stderr[-1400:]}")
        cls.grain = json.loads(output_file.read_text())

    @classmethod
    def tearDownClass(cls):
        cls.room.cleanup()

    def test_continuity_01_source_drift_by_content(self):
        rows = [quad(GRAPH, "urn:test:a", "urn:test:p", "one", "literal")]
        original = {"quads": rows, "quadCount": 1,
                    "quadDigest": continuity.snapshot_lib.digest(rows), "graphCounts": {GRAPH: 1}}
        changed = [quad(GRAPH, "urn:test:a", "urn:test:p", "two", "literal")]
        current = {"quadCount": 1, "quadDigest": continuity.snapshot_lib.digest(changed),
                   "graphCounts": {GRAPH: 1}}
        self.assertFalse(continuity.snapshot_matches(original, current))

    def test_continuity_02_every_resource_disposition(self):
        p = self.package
        orphan = resource("metadata-1", "journal", "urn:test:metadata", {"quads": []}, source=None)
        q = {"snapshot-id": p["snapshot-id"], "resources": [orphan]}
        result = continuity.dispositions(p, q, {"metadata-1": {"decision": "exclude-metadata",
            "owner-approved": True, "evidence-digest": "reviewed-evidence"}})
        self.assertEqual((5, 4, 0, 1), (result["source-resources"], result["mapped"],
                                         result["quarantined"], result["excluded-metadata"]))
        self.assertNotIn("metadata-1", continuity.source_semantics(p)["items"])

    def test_continuity_03_unresolved_metadata_blocks(self):
        orphan = resource("metadata-1", "journal", "urn:test:metadata", {"quads": []}, source=None)
        result = continuity.dispositions(self.package,
                                          {"snapshot-id": self.package["snapshot-id"], "resources": [orphan]})
        self.assertEqual(1, result["quarantined"])
        self.assertEqual("quarantined", result["decisions"][0]["status"])

    def test_continuity_04_resume_once(self):
        self.assertEqual("reused", self.grain["replay"]["status"])
        self.assertEqual(4, self.grain["reconciliation"]["source-count"])
        self.assertTrue(self.grain["reconciliation"]["matched?"])
        self.assertEqual("source", self.grain["prior"]["read-source"])

    def test_continuity_05_review_parity(self):
        source = continuity.source_semantics(self.package)
        self.assertEqual([], continuity.compare_shadow(source, self.grain["shadow"]))
        changed = json.loads(json.dumps(self.grain["shadow"]))
        changed["items"]["journal-1"]["reviews"].reverse()
        self.assertEqual("item-or-review", continuity.compare_shadow(source, changed)[0]["class"])

    def test_continuity_06_daily_versions(self):
        source = continuity.source_semantics(self.package)
        self.assertEqual([], continuity.compare_shadow(source, self.grain["shadow"]))
        changed = json.loads(json.dumps(self.grain["shadow"]))
        changed["accounts"].pop("daily-1")
        self.assertEqual("daily-version", continuity.compare_shadow(source, changed)[0]["class"])

    def test_continuity_07_replay_and_graph(self):
        self.assertEqual(continuity.without_graph_count(self.grain["shadow"]),
                         continuity.without_graph_count(self.grain["after-restart"]))
        self.assertEqual(self.grain["after-graph"], self.grain["after-rebuild"])
        self.assertEqual(1, self.grain["after-graph"]["owner-concept-count"])

    def test_continuity_08_rollback(self):
        self.assertEqual("import", self.grain["selected"]["read-source"])
        self.assertEqual(self.grain["prior"], self.grain["restored"])
        self.assertEqual("source", self.grain["restored"]["read-source"])
        self.assertTrue(self.grain["retained-after-rollback"]["matched?"])

    def test_continuity_09_approval_separate(self):
        snapshot = {"quads": [], "quadCount": 0,
                    "quadDigest": continuity.snapshot_lib.digest([]), "graphCounts": {}}
        current = {"quadCount": 0, "quadDigest": snapshot["quadDigest"], "graphCounts": {}}
        _, report = continuity.qualify(snapshot, self.package,
            {"snapshot-id": self.package["snapshot-id"], "resources": []}, current, self.grain)
        self.assertFalse(report["live-switch-approved"])
        self.assertEqual("source", self.grain["restored"]["read-source"])


if __name__ == "__main__":
    unittest.main()
