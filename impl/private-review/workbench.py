#!/usr/bin/env python3
"""Owner-only local review workbench. Private rows never enter Grain or stdout."""
from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
import secrets
import stat
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
IMPL = REPO / "impl"
QUALITY_RUNNER = IMPL / "runtime/products/voice-agent/apps/pi-agent/knowledge-quality-run.mjs"
MARKER = "AWP_WORKBENCH_RESULT:"


def canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()


def digest(value: object) -> str:
    return hashlib.sha256(canonical(value)).hexdigest()


def opaque(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()[:32]


def private_root(value: str | Path) -> Path:
    path = Path(value).expanduser().resolve()
    if path == REPO or REPO in path.parents:
        raise ValueError("private workspace must be outside repository")
    if path.exists() and (not path.is_dir() or stat.S_IMODE(path.stat().st_mode) & 0o077):
        raise ValueError("private workspace must have owner-only permissions")
    return path


def private_input(value: str | Path) -> Path:
    path = Path(value).expanduser()
    if path.is_symlink():
        raise ValueError("private input symlink refused")
    path = path.resolve()
    if path == REPO or REPO in path.parents or not path.is_file():
        raise ValueError("private input must be a file outside repository")
    if stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise ValueError("private input must be owner-only")
    return path


def write_new(path: Path, value: object) -> None:
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    if stat.S_IMODE(path.parent.stat().st_mode) & 0o077:
        raise ValueError("private parent must be owner-only")
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, "wb") as output:
        output.write(canonical(value) + b"\n")


def write_secret(path: Path, value: str) -> None:
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as output:
        output.write(value + "\n")


def replace_private(path: Path, value: object) -> None:
    temporary = path.with_name(path.name + "." + secrets.token_hex(8))
    write_new(temporary, value)
    os.replace(temporary, path)


def read_private(path: Path) -> object:
    if path.is_symlink() or stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise ValueError("private file permission failure")
    return json.loads(path.read_text(encoding="utf-8"))


class GrainClient:
    """One process per query or command; only a marked, content-free result is parsed."""

    def __init__(self, storage: Path, reviewer_config: Path):
        self.storage = storage
        self.reviewer_config = reviewer_config

    def request(self, payload: dict) -> dict:
        command = ["clojure", "-J--add-opens=java.base/java.nio=ALL-UNNAMED",
                   "-J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED", "-M", "-m",
                   "awp-v2.knowledge.workbench-tool", str(self.storage), str(self.reviewer_config)]
        result = subprocess.run(command, input=canonical(payload), stdout=subprocess.PIPE,
                                stderr=subprocess.DEVNULL, cwd=IMPL, timeout=120, check=False)
        if result.returncode:
            raise RuntimeError("review authority unavailable")
        output = result.stdout.decode(errors="replace")
        if MARKER not in output:
            raise RuntimeError("review authority did not answer")
        answer = json.loads(output.rsplit(MARKER, 1)[1].splitlines()[0])
        if answer.get("error"):
            raise ValueError(f"review authority refused: {answer['error']}")
        return answer

    def command(self, owner: str, actor: str, operations: list[dict]) -> dict:
        return self.request({"owner": owner, "actor": actor, "operations": operations})

    def view(self, owner: str) -> dict:
        return self.request({"mode": "view", "owner": owner, "actor": owner})


class PrivateWorkbench:
    def __init__(self, workspace: str | Path, grain: GrainClient | object | None = None):
        self.root = private_root(workspace)
        config = read_private(self.root / "config.json")
        self.owner = config["owner"]
        self.candidates = read_private(self.root / "candidates.json")
        self.quarantine = read_private(self.root / "quarantine.json")
        self.snapshot = read_private(self.root / "snapshot.json") if (self.root / "snapshot.json").exists() else None
        self.grain = grain or GrainClient(self.root / "grain", self.root / "reviewers.json")
        self._cases = {opaque(f"{self.owner}|case|{row['id']}"): row for row in self.candidates["cases"]}
        self._metadata = {opaque(f"{self.owner}|metadata|{row['legacy-id']}"): row
                          for row in self.quarantine["resources"]}
        if len(self._cases) != len(self.candidates["cases"]) or len(self._metadata) != len(self.quarantine["resources"]):
            raise ValueError("duplicate private inventory identity")
        self._view = None

    @classmethod
    def create(cls, workspace: str | Path, owner: str, candidates: str | Path,
               quarantine: str | Path, snapshot: str | Path | None = None,
               grain: GrainClient | object | None = None) -> "PrivateWorkbench":
        root = private_root(workspace)
        if not owner or root.exists():
            raise ValueError("new private workspace and explicit owner required")
        source_cases = read_private(private_input(candidates))
        source_quarantine = read_private(private_input(quarantine))
        source_snapshot = read_private(private_input(snapshot)) if snapshot else None
        if source_cases.get("formatVersion") != 2 or not isinstance(source_cases.get("cases"), list):
            raise ValueError("candidate inventory must use private corpus format 2")
        if not isinstance(source_quarantine.get("resources"), list) or not source_quarantine.get("snapshot-id"):
            raise ValueError("quarantine inventory incomplete")
        if source_snapshot and (source_snapshot.get("quadDigest") != source_quarantine["snapshot-id"]
                                or digest(source_snapshot.get("quads")) != source_snapshot["quadDigest"]):
            raise ValueError("frozen snapshot mismatch")
        root.mkdir(mode=0o700)
        (root / "grain").mkdir(mode=0o700)
        (root / "actions").mkdir(mode=0o700)
        (root / "drafts").mkdir(mode=0o700)
        write_new(root / "config.json", {"owner": owner, "createdAt": datetime.now(timezone.utc).isoformat()})
        write_new(root / "candidates.json", source_cases)
        write_new(root / "quarantine.json", source_quarantine)
        if source_snapshot:
            write_new(root / "snapshot.json", source_snapshot)
        write_new(root / "reviewers.json", {"reviewers": []})
        owner_token = secrets.token_urlsafe(32)
        write_secret(root / "owner-token.txt", owner_token)
        write_new(root / "tokens.json", {"owner": hashlib.sha256(owner_token.encode()).hexdigest(),
                                          "reviewers": {}})
        workbench = cls(root, grain)
        workbench.import_inventories()
        return workbench

    def add_reviewer(self, reviewer_id: str, token_file: str | Path) -> dict:
        if not reviewer_id or reviewer_id == self.owner or len(reviewer_id) > 128:
            raise ValueError("distinct reviewer identity required")
        target = private_root(Path(token_file).expanduser().resolve().parent) / Path(token_file).name
        if target.exists():
            raise ValueError("new private token file required")
        tokens = read_private(self.root / "tokens.json")
        if reviewer_id in tokens["reviewers"]:
            raise ValueError("reviewer already exists")
        token = secrets.token_urlsafe(32)
        write_secret(target, token)
        tokens["reviewers"][reviewer_id] = hashlib.sha256(token.encode()).hexdigest()
        replace_private(self.root / "tokens.json", tokens)
        reviewers = read_private(self.root / "reviewers.json")
        reviewers["reviewers"].append(reviewer_id)
        replace_private(self.root / "reviewers.json", reviewers)
        return {"status": "reviewer-added"}

    def authenticate(self, token: str) -> tuple[str, str] | None:
        given = hashlib.sha256(token.encode()).hexdigest()
        tokens = read_private(self.root / "tokens.json")
        if hmac.compare_digest(given, tokens["owner"]):
            return self.owner, "owner"
        for reviewer, expected in tokens["reviewers"].items():
            if hmac.compare_digest(given, expected):
                return reviewer, "reviewer"
        return None

    def import_inventories(self) -> dict:
        operations = []
        for case_id, row in self._cases.items():
            if not row.get("sourceText") or row.get("branch") not in {"journal", "assertion", "concept", "relation"}:
                raise ValueError("invalid candidate row")
            operations.append({"op": "workbench-case", "data": {
                "case-id": case_id, "source-id": opaque(str(row.get("sourceId", row["id"]))),
                "source-digest": digest(row["sourceText"]), "branch": row["branch"]}})
        for resource_id, row in self._metadata.items():
            operations.append({"op": "workbench-metadata", "data": {
                "snapshot-id": self.quarantine["snapshot-id"], "snapshot-digest": self.quarantine["snapshot-id"],
                "legacy-id": resource_id, "resource-digest": digest(row)}})
        result = self.grain.command(self.owner, self.owner, operations) if operations else {"results": []}
        self._view = None
        return {"cases": len(self._cases), "metadata": len(self._metadata), "imported": len(result.get("results", []))}

    def view(self) -> dict:
        if self._view is None:
            self._view = self.grain.view(self.owner)
        return self._view

    def summary(self) -> dict:
        view = self.view()
        cases = view.get("cases", [])
        metadata = view.get("metadata", [])
        if len(cases) != len(self._cases) or len(metadata) != len(self._metadata):
            raise ValueError("private inventory and review authority differ")
        return {"quality": {"total": len(cases),
                            "pending": sum(row["review-status"] == "pending" for row in cases),
                            "privacyPending": sum(row["privacy-status"] == "pending" for row in cases),
                            "eligible": sum(row["review-status"] in {"approved", "adjudicated"}
                                            and row["privacy-status"] == "passed"
                                            and row["split"] != "unassigned" for row in cases),
                            "gate": "open"},
                "migration": {"total": len(metadata),
                              "quarantined": sum(row["disposition"] == "quarantined" for row in metadata),
                              "excluded": sum(row["disposition"] == "excluded-metadata" for row in metadata),
                              "linked": sum(row["disposition"] == "linked" for row in metadata),
                              "gate": "open"}, "liveReadChoice": "unchanged"}

    def list_rows(self, kind: str) -> list[dict]:
        field = "cases" if kind == "cases" else "metadata" if kind == "metadata" else None
        if not field:
            raise ValueError("unknown queue")
        selected = self.view()[field]
        key = "case-id" if field == "cases" else "legacy-id"
        allowed = {"case-id", "branch", "review-status", "privacy-status", "family-id", "split", "revision"} \
            if field == "cases" else {"legacy-id", "disposition", "revision"}
        return [{field: row[field] for field in allowed if field in row} for row in sorted(selected, key=lambda r: r[key])]

    def row(self, kind: str, row_id: str) -> dict:
        field = "cases" if kind == "cases" else "metadata" if kind == "metadata" else None
        private = self._cases if field == "cases" else self._metadata if field == "metadata" else None
        if private is None or row_id not in private:
            raise ValueError("row unavailable")
        key = "case-id" if field == "cases" else "legacy-id"
        state = next((row for row in self.view()[field] if row[key] == row_id), None)
        if not state:
            raise ValueError("row unavailable")
        result = {"id": row_id, "state": state, "source": private[row_id]}
        draft_path = self.root / "drafts" / f"{row_id}.json"
        if draft_path.exists():
            result["draft"] = read_private(draft_path)
        return result

    def save_draft(self, row_id: str, draft: dict) -> dict:
        if row_id not in self._cases or set(draft) - {"expected", "note", "familyId", "split"}:
            raise ValueError("invalid draft")
        path = self.root / "drafts" / f"{row_id}.json"
        temporary = path.with_name(path.name + "." + secrets.token_hex(8))
        write_new(temporary, draft)
        os.replace(temporary, path)
        return {"status": "draft-saved", "id": row_id}

    def _private_action(self, action_id: str, payload: dict) -> None:
        if not action_id or len(action_id) > 128:
            raise ValueError("action identity required")
        path = self.root / "actions" / f"{opaque(action_id)}.json"
        if path.exists():
            if read_private(path) != payload:
                raise ValueError("action identity conflicts")
        else:
            write_new(path, payload)

    def _action(self, op: str, actor: str, data: dict, payload: dict | None = None) -> dict:
        if payload is not None:
            self._private_action(data["action-id"], payload)
        result = self.grain.command(self.owner, actor, [{"op": op, "data": data}])
        self._view = None
        return result["results"][0]

    def assign_family(self, case_id: str, family_id: str, split: str, revision: int,
                      action_id: str, actor: str) -> dict:
        if actor != self.owner or case_id not in self._cases or not family_id or split not in \
                {"development", "challenge", "locked_holdout", "production_shadow"}:
            raise ValueError("invalid family decision")
        return self._action("workbench-family", actor, {"case-id": case_id, "family-id": family_id,
                           "split": split, "expected-revision": revision, "action-id": action_id,
                           "at": datetime.now(timezone.utc).isoformat()})

    def review_case(self, case_id: str, expected: list, decision: str, revision: int,
                    action_id: str, actor: str, role: str = "independent", note: str = "") -> dict:
        if case_id not in self._cases or actor == self.owner or role not in {"independent", "adjudicator"}:
            raise ValueError("independent reviewer required")
        if decision not in ({"approved", "rejected", "disputed"} if role == "independent" else {"adjudicated"}):
            raise ValueError("invalid review decision")
        if not isinstance(expected, list) or any(not isinstance(item, dict) or
                not isinstance(item.get("kind"), str) or not isinstance(item.get("evidence"), str) or
                not item["evidence"] or item["evidence"] not in self._cases[case_id]["sourceText"] or
                item.get("origin") not in {"observed", "miss", "correction"} for item in expected):
            raise ValueError("expected items need exact source anchors and origin")
        payload = {"expected": expected, "note": note}
        return self._action("workbench-review", actor, {"case-id": case_id,
                            "expected-digest": digest(expected), "decision": decision,
                            "reviewer-role": role, "expected-revision": revision,
                            "action-id": action_id, "at": datetime.now(timezone.utc).isoformat()}, payload)

    def privacy_review(self, case_id: str, decision: str, revision: int,
                       action_id: str, actor: str) -> dict:
        if actor != self.owner or case_id not in self._cases or decision not in {"passed", "failed"}:
            raise ValueError("owner privacy decision required")
        return self._action("workbench-privacy", actor, {"case-id": case_id, "decision": decision,
                            "expected-revision": revision, "action-id": action_id,
                            "at": datetime.now(timezone.utc).isoformat()})

    def disposition(self, resource_id: str, disposition: str, revision: int, action_id: str,
                    actor: str, reason: str = "", evidence: str = "", source_id: str = "") -> dict:
        if actor != self.owner or resource_id not in self._metadata or disposition not in \
                {"quarantined", "linked", "excluded-metadata"}:
            raise ValueError("owner disposition required")
        if disposition != "quarantined" and (not reason.strip() or not evidence.strip()):
            raise ValueError("reason and evidence required")
        verified = ""
        if disposition == "linked":
            if not self.snapshot or not source_id:
                raise ValueError("frozen source link unavailable")
            resource = self._metadata[resource_id]
            source_refs = resource.get("value", {}).get("sourceRefs") or []
            subjects = {row["s"]["value"] for row in self.snapshot["quads"]}
            if source_id not in source_refs or source_id not in subjects:
                raise ValueError("frozen source does not prove link")
            verified = digest([self.quarantine["snapshot-id"], resource["legacy-id"], source_id, evidence])
        payload = {"reason": reason, "evidence": evidence, "sourceId": source_id}
        return self._action("workbench-disposition", actor, {
            "snapshot-id": self.quarantine["snapshot-id"], "snapshot-digest": self.quarantine["snapshot-id"],
            "legacy-id": resource_id, "disposition": disposition, "source-id": opaque(source_id) if source_id else "",
            "reason-digest": digest(reason) if reason else "", "evidence-digest": digest(evidence) if evidence else "",
            "verified-link-digest": verified, "expected-revision": revision,
            "action-id": action_id, "at": datetime.now(timezone.utc).isoformat()}, payload)

    def private_reports(self, output_dir: str | Path) -> dict:
        target = private_root(output_dir)
        if target.exists():
            raise ValueError("new report directory required")
        target.mkdir(mode=0o700)
        view = self.view()
        cases = []
        for state in view["cases"]:
            private = self._cases[state["case-id"]]
            actions = state.get("history", [])
            final = next((entry for entry in reversed(actions) if entry.get("op") == "workbench-review"), None)
            expected = None
            if final:
                action_file = self.root / "actions" / f"{opaque(final['action-id'])}.json"
                payload = read_private(action_file)
                if digest(payload["expected"]) != state["expected-digest"]:
                    raise ValueError("private review payload differs from authority")
                expected = payload["expected"]
            privacy = next((entry for entry in reversed(actions) if entry.get("op") == "workbench-privacy"), None)
            reviews = [{"actorId": entry["actor"], "role": entry["reviewer-role"],
                        "decision": "approved" if entry["decision"] == "adjudicated" else entry["decision"],
                        "at": entry["at"]} for entry in actions if entry.get("op") == "workbench-review"]
            cases.append({**private, "id": state["case-id"], "owner": self.owner,
                          "semanticFamilyId": state["family-id"], "split": state["split"],
                          "annotationStatus": "adjudicated" if state["review-status"] == "adjudicated"
                            else "reviewed" if state["review-status"] == "approved" else "pending",
                          "privacyReview": {"status": state["privacy-status"],
                                            "reviewerId": privacy["actor"] if privacy else "",
                                            "reviewedAt": privacy["at"] if privacy else ""},
                          "reviews": reviews, "expected": expected})
        corpus = {"formatVersion": 2, "datasetId": self.candidates["datasetId"], "cases": cases}
        write_new(target / "quality-corpus.json", corpus)
        quality_result = subprocess.run(["node", str(QUALITY_RUNNER), "readiness",
                                         str(target / "quality-corpus.json"), str(target / "quality-readiness.json")],
                                        cwd=IMPL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                        timeout=60, check=False)
        if quality_result.returncode:
            raise RuntimeError("quality readiness unavailable")
        readiness = read_private(target / "quality-readiness.json")
        dispositions = []
        approvals = {}
        for state in view["metadata"]:
            private = self._metadata[state["legacy-id"]]
            history = state.get("history", [])
            final = history[-1] if history else None
            payload = read_private(self.root / "actions" / f"{opaque(final['action-id'])}.json") if final else None
            item = {"legacy-id": private["legacy-id"], "status": state["disposition"],
                    "snapshot-id": state["snapshot-id"], "history": history, "reason": payload["reason"] if payload else "",
                    "evidence": payload["evidence"] if payload else ""}
            dispositions.append(item)
            if state["disposition"] == "excluded-metadata" and payload:
                approvals[private["legacy-id"]] = {"decision": "exclude-metadata", "owner-approved": True,
                    "evidence-digest": state["evidence-digest"], "reason-digest": state["reason-digest"],
                    "action-id": final["action-id"]}
        write_new(target / "metadata-dispositions.json", {"snapshot-id": self.quarantine["snapshot-id"],
                                                          "records": dispositions})
        write_new(target / "continuity-approvals.json", approvals)
        summary = self.summary()
        summary["quality"]["readiness"] = readiness["status"]
        summary["quality"]["reasons"] = readiness["reasons"]
        summary["migration"]["gate"] = "open" if summary["migration"]["quarantined"] or \
            summary["migration"]["linked"] else "candidate-for-shadow-recheck"
        write_new(target / "private-summary.json", summary)
        return {"quality": summary["quality"]["gate"], "qualityReadiness": readiness["status"],
                "migration": summary["migration"]["gate"],
                "cases": summary["quality"]["total"], "metadata": summary["migration"]["total"],
                "liveReadChoice": "unchanged"}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Private owner review workbench")
    sub = parser.add_subparsers(dest="command", required=True)
    init = sub.add_parser("init", help="copy private inventories into a new owner-only workspace")
    init.add_argument("--workspace", required=True)
    init.add_argument("--owner", required=True)
    init.add_argument("--candidates", required=True)
    init.add_argument("--quarantine", required=True)
    init.add_argument("--snapshot")
    report = sub.add_parser("report", help="write owner-only readiness and disposition reports")
    report.add_argument("--workspace", required=True)
    report.add_argument("--output-dir", required=True)
    reviewer = sub.add_parser("add-reviewer", help="issue a separate private reviewer credential")
    reviewer.add_argument("--workspace", required=True)
    reviewer.add_argument("--reviewer", required=True)
    reviewer.add_argument("--token-file", required=True)
    serve = sub.add_parser("serve", help="open a loopback-only review page")
    serve.add_argument("--workspace", required=True)
    serve.add_argument("--port", type=int, default=0)
    args = parser.parse_args(argv)
    try:
        if args.command == "init":
            result = PrivateWorkbench.create(args.workspace, args.owner, args.candidates,
                                             args.quarantine, args.snapshot).summary()
            print(json.dumps(result, sort_keys=True))
        elif args.command == "report":
            print(json.dumps(PrivateWorkbench(args.workspace).private_reports(args.output_dir), sort_keys=True))
        elif args.command == "add-reviewer":
            print(json.dumps(PrivateWorkbench(args.workspace).add_reviewer(args.reviewer, args.token_file)))
        else:
            from server import serve as serve_workbench
            serve_workbench(PrivateWorkbench(args.workspace), args.port)
        return 0
    except (ValueError, RuntimeError, OSError) as error:
        print(f"workbench error: {type(error).__name__}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
