import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("snapshot", Path(__file__).with_name("snapshot.py"))
snapshot = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(snapshot)


def cell(value, kind="uri"):
    return {"type": kind, "value": value}


def quad(graph, subject, predicate, obj, kind="uri"):
    return {"g": cell(graph), "s": cell(subject), "p": cell(predicate), "o": cell(obj, kind)}


class SnapshotTest(unittest.TestCase):
    def test_normalize_preserves_review_lineage_and_quarantines_orphans(self):
        graph = "urn:awp:digital-twin:graph:journal"
        rows = [
            quad(graph, "urn:source", snapshot.IDENTIFIER, "c:m", "literal"),
            quad(graph, "urn:entry", "http://www.w3.org/ns/prov#wasDerivedFrom", "urn:source"),
            quad("urn:awp:digital-twin:graph:journal-feedback", "urn:review", "https://schema.org/isBasedOn", "urn:entry"),
            quad("urn:awp:digital-twin:graph:accepted", "urn:orphan", "https://schema.org/name", "Unknown", "literal"),
        ]
        rows.sort(key=lambda row: snapshot.canonical(row))
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source = root / "source.json"
            source.write_text(json.dumps({"quadDigest": snapshot.digest(rows), "quadCount": len(rows),
                                          "graphCounts": {}, "quads": rows}))
            mapping = root / "owners.json"
            mapping.write_text(json.dumps({"c:m": "owner@example.test"}))
            report = snapshot.normalize(source, mapping, root / "out")
            self.assertEqual(report["mappedResources"], 3)
            self.assertEqual(report["quarantinedResources"], 1)
            packages = list((root / "out").glob("owner-*.json"))
            resources = json.loads(packages[0].read_text())["resources"]
            self.assertEqual({r["source-id"] for r in resources}, {"c:m"})
            self.assertEqual(len(json.loads((root / "out" / "quarantine.json").read_text())["resources"]), 1)

    def test_private_files_cannot_be_written_inside_repo(self):
        with self.assertRaises(SystemExit):
            snapshot.private_path(str(snapshot.REPO / "private.json"))


if __name__ == "__main__":
    unittest.main()
