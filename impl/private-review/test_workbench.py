"""Slice 21 private surface acceptance; Grain command rules have their own suite."""
import json
import stat
import tempfile
import threading
import unittest
from http.server import HTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from server import handler_for
from workbench import PrivateWorkbench, digest, write_new


class FakeGrain:
    def __init__(self):
        self.cases, self.metadata, self.actions, self.operations = {}, {}, {}, []

    def command(self, owner, actor, operations):
        answers = []
        for entry in operations:
            op, data = entry["op"], entry["data"]
            self.operations.append((owner, actor, op, data))
            if op == "workbench-case":
                self.cases.setdefault(data["case-id"], {**data, "revision": 0, "family-id": "", "split": "unassigned",
                    "review-status": "pending", "privacy-status": "pending", "expected-digest": "",
                    "last-reviewer": "", "history": []})
                answers.append({"status": "registered"}); continue
            if op == "workbench-metadata":
                self.metadata.setdefault(data["legacy-id"], {**data, "revision": 0, "disposition": "quarantined",
                    "history": [], "source-id": "", "reason-digest": "", "evidence-digest": ""})
                answers.append({"status": "registered"}); continue
            row = self.metadata[data["legacy-id"]] if op == "workbench-disposition" else self.cases[data["case-id"]]
            if data["action-id"] in self.actions:
                if self.actions[data["action-id"]] != (actor, op, {k:v for k,v in data.items() if k != "at"}):
                    raise ValueError("conflicting action")
                answers.append({"status": "reused"}); continue
            if row["revision"] != data["expected-revision"]: raise ValueError("stale revision")
            if op == "workbench-family":
                if any(other is not row and other["family-id"] == data["family-id"] and other["split"] != data["split"]
                       for other in self.cases.values()): raise ValueError("family conflict")
                row["family-id"], row["split"] = data["family-id"], data["split"]
            elif op == "workbench-review":
                if actor == owner or row["review-status"] == "disputed" and actor == row["last-reviewer"]:
                    raise ValueError("reviewer not independent")
                row["review-status"], row["expected-digest"], row["last-reviewer"] = \
                    data["decision"], data["expected-digest"], actor
            elif op == "workbench-privacy":
                if actor != owner: raise ValueError("privacy owner required")
                row["privacy-status"] = data["decision"]
            else:
                if actor != owner: raise ValueError("metadata owner required")
                row["disposition"] = data["disposition"]
                for key in ("source-id", "reason-digest", "evidence-digest"): row[key] = data[key]
            row["history"].append({**data, "op": op, "actor": actor})
            row["revision"] += 1
            self.actions[data["action-id"]] = (actor, op, {k:v for k,v in data.items() if k != "at"})
            answers.append({"status": "recorded", "revision": row["revision"]})
        return {"results": answers}

    def view(self, owner):
        return {"cases": [dict(item) for item in self.cases.values()],
                "metadata": [dict(item) for item in self.metadata.values()]}


class WorkbenchAcceptance(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="awp-wb-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.grain = FakeGrain()
        self.source = "I will finish the draft tomorrow."
        write_new(self.root / "cases.json", {"formatVersion": 2, "datasetId": "synthetic", "cases": [
            {"id": name, "sourceId": name, "sourceText": self.source, "branch": branch,
             "sourceKind": "synthetic", "errorSlice": "unassigned", "sampleKind": "unassigned"}
            for name, branch in [("first", "journal"), ("second", "assertion")]]})
        write_new(self.root / "quarantine.json", {"snapshot-id": "synthetic-snapshot", "resources": [
            {"legacy-id": name, "reason": "owner-or-source-ambiguous", "value": {"label": "synthetic", "sourceRefs": []}}
            for name in ("urn:synthetic:one", "urn:synthetic:two")]})
        self.w = PrivateWorkbench.create(self.root / "private", "owner@example.test", self.root / "cases.json",
                                         self.root / "quarantine.json", grain=self.grain)
        self.cases = sorted(self.w._cases)
        self.metadata = sorted(self.w._metadata)

    def test_workbench_001_pending_inventories(self):
        s = self.w.summary()
        self.assertEqual((2, 2, 0), (s["quality"]["total"], s["quality"]["pending"], s["quality"]["eligible"]))
        self.assertEqual((2, 2), (s["migration"]["total"], s["migration"]["quarantined"]))

    def test_workbench_002_resume_and_idempotence(self):
        item = self.cases[0]
        self.w.save_draft(item, {"expected": [], "note": "synthetic draft"})
        self.w.assign_family(item, "family", "development", 0, "action-family", self.w.owner)
        reopened = PrivateWorkbench(self.w.root, grain=self.grain)
        self.assertEqual("synthetic draft", reopened.row("cases", item)["draft"]["note"])
        self.assertEqual("reused", reopened.assign_family(item, "family", "development", 0, "action-family", self.w.owner)["status"])
        self.assertEqual(1, reopened.row("cases", item)["state"]["revision"])

    def test_workbench_003_private_source(self):
        self.assertEqual(0o700, stat.S_IMODE(self.w.root.stat().st_mode))
        self.assertEqual(0o600, stat.S_IMODE((self.w.root / "candidates.json").stat().st_mode))
        self.assertNotIn(self.source, json.dumps(self.grain.operations))
        self.assertNotIn("urn:synthetic:one", json.dumps(self.grain.operations))

    def test_workbench_004_anchored_expected_answers(self):
        item = self.cases[0]
        with self.assertRaises(ValueError):
            self.w.review_case(item, [{"kind":"decision","evidence":"absent","origin":"miss"}],
                               "approved", 0, "bad", "reviewer@example.test")
        expected = [{"kind":"decision","evidence":"finish the draft","origin":"miss"}]
        self.w.review_case(item, expected, "approved", 0, "good", "reviewer@example.test")
        self.assertEqual(digest(expected), self.w.row("cases", item)["state"]["expected-digest"])

    def test_workbench_005_empty_and_privacy(self):
        item = self.cases[0]
        self.w.review_case(item, [], "approved", 0, "empty", "reviewer@example.test")
        self.assertEqual(0, self.w.summary()["quality"]["eligible"])
        self.w.privacy_review(item, "passed", 1, "privacy", self.w.owner)
        self.assertEqual(0, self.w.summary()["quality"]["eligible"])

    def test_workbench_006_separate_adjudicator(self):
        item = self.cases[0]
        self.w.review_case(item, [], "disputed", 0, "dispute", "reviewer@example.test")
        with self.assertRaises(ValueError):
            self.w.review_case(item, [], "adjudicated", 1, "self", "reviewer@example.test", "adjudicator")
        self.w.review_case(item, [], "adjudicated", 1, "other", "other-reviewer@example.test", "adjudicator")
        self.assertEqual(2, len(self.w.row("cases", item)["state"]["history"]))

    def test_workbench_007_family_partition(self):
        a, b = self.cases
        self.w.assign_family(a, "same-family", "development", 0, "a", self.w.owner)
        with self.assertRaises(ValueError): self.w.assign_family(b, "same-family", "challenge", 0, "b", self.w.owner)
        self.assertEqual("unassigned", self.w.row("cases", b)["state"]["split"])

    def test_workbench_008_unresolved_metadata(self):
        row = self.w.row("metadata", self.metadata[0])
        self.assertEqual("quarantined", row["state"]["disposition"])
        self.assertEqual("owner-or-source-ambiguous", row["source"]["reason"])

    def test_workbench_009_frozen_source_link(self):
        with self.assertRaises(ValueError): self.w.disposition(self.metadata[0], "linked", 0, "link", self.w.owner,
                                                               "reason", "evidence", "urn:missing")
        self.assertEqual("quarantined", self.w.row("metadata", self.metadata[0])["state"]["disposition"])

    def test_workbench_010_evidenced_exclusion(self):
        item = self.metadata[0]
        with self.assertRaises(ValueError): self.w.disposition(item, "excluded-metadata", 0, "bad", self.w.owner)
        self.w.disposition(item, "excluded-metadata", 0, "good", self.w.owner, "synthetic reason", "synthetic proof")
        self.w.private_reports(self.root / "reports")
        approvals = json.loads((self.root / "reports/continuity-approvals.json").read_text())
        self.assertEqual(1, len(approvals))
        self.assertEqual(2, self.w.summary()["migration"]["total"])

    def test_workbench_011_stale_and_foreign_actions(self):
        item = self.cases[0]
        self.w.assign_family(item, "family", "development", 0, "first", self.w.owner)
        with self.assertRaises(ValueError): self.w.assign_family(item, "other", "development", 0, "stale", self.w.owner)
        with self.assertRaises(ValueError): self.w.privacy_review(item, "passed", 1, "foreign", "other-owner")
        server = HTTPServer(("127.0.0.1", 0), handler_for(self.w))
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        self.addCleanup(server.server_close); self.addCleanup(server.shutdown)
        url = f"http://127.0.0.1:{server.server_port}/api/summary"
        with self.assertRaises(HTTPError) as refused: urlopen(url, timeout=3)
        self.assertEqual(401, refused.exception.code)
        refused.exception.close()
        token = (self.w.root / "owner-token.txt").read_text().strip()
        with urlopen(Request(url, headers={"Authorization": f"Bearer {token}"}), timeout=3) as response:
            self.assertEqual(2, json.load(response)["summary"]["quality"]["total"])

    def test_workbench_012_replay(self):
        self.w.privacy_review(self.cases[0], "failed", 0, "fail", self.w.owner)
        self.assertEqual(self.w.view(), PrivateWorkbench(self.w.root, grain=self.grain).view())
        self.assertEqual("unchanged", self.w.summary()["liveReadChoice"])

    def test_workbench_013_separate_gates(self):
        self.w.review_case(self.cases[0], [], "approved", 0, "review", "reviewer@example.test")
        result = self.w.private_reports(self.root / "reports")
        self.assertEqual(("open", "open", "unchanged"), (result["quality"], result["migration"], result["liveReadChoice"]))
        self.assertEqual(0o600, stat.S_IMODE((self.root / "reports/quality-corpus.json").stat().st_mode))


if __name__ == "__main__": unittest.main()
