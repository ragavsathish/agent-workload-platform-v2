#!/usr/bin/env python3
"""Private source/shadow reconciliation. Never prints source rows or owner text."""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import subprocess
import sys
import tempfile
from collections import Counter, defaultdict
from pathlib import Path
from urllib.request import Request, urlopen

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
SPEC = importlib.util.spec_from_file_location("awp_snapshot", HERE / "snapshot.py")
snapshot_lib = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(snapshot_lib)


def private_path(path: str) -> Path:
    return snapshot_lib.private_path(path)


def private_write(path: Path, value: object) -> None:
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    with open(path, "x", encoding="utf-8", opener=lambda name, flags: os.open(name, flags, 0o600)) as out:
        json.dump(value, out, sort_keys=True, ensure_ascii=False)


def read_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def verify_snapshot(data: dict) -> bool:
    rows = data["quads"]
    return data["quadCount"] == len(rows) and data["quadDigest"] == snapshot_lib.digest(rows)


def live_source_identity(url: str, token: str | None = None) -> dict:
    from urllib.parse import urlparse
    parsed = urlparse(url)
    if parsed.scheme != "http" or parsed.hostname not in {"localhost", "127.0.0.1"} or parsed.path != "/query":
        raise ValueError("source query must be loopback /query")
    headers = {"Content-Type": "application/sparql-query", "Accept": "application/sparql-results+json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = Request(url, snapshot_lib.QUERY.encode(), headers, method="POST")
    with urlopen(request, timeout=60) as response:
        payload = json.load(response)
    rows = [{k: binding[k] for k in ("g", "s", "p", "o")}
            for binding in payload["results"]["bindings"]]
    rows.sort(key=snapshot_lib.canonical)
    return {"quadCount": len(rows), "quadDigest": snapshot_lib.digest(rows),
            "graphCounts": dict(sorted(Counter(r["g"]["value"] for r in rows).items()))}


def snapshot_matches(snapshot: dict, current: dict) -> bool:
    return (verify_snapshot(snapshot) and snapshot["quadCount"] == current["quadCount"]
            and snapshot["quadDigest"] == current["quadDigest"]
            and snapshot["graphCounts"] == current["graphCounts"])


def package_matches_snapshot(source: dict, package: dict, quarantine: dict) -> bool:
    expected_ids = {f'{q["g"]["value"]}|{q["s"]["value"]}' for q in source["quads"]}
    mapped = package["resources"]
    held = quarantine["resources"]
    actual_ids = [r["legacy-id"] for r in mapped + held]
    return (package.get("snapshot-id") == source["quadDigest"]
            and package.get("source-quad-digest") == source["quadDigest"]
            and quarantine.get("snapshot-id") == source["quadDigest"]
            and package.get("digest") == snapshot_lib.digest(mapped)
            and len(actual_ids) == len(set(actual_ids))
            and set(actual_ids) == expected_ids)


def owner_map_reproduces_package(snapshot_file: Path, owner_map_file: Path,
                                 package: dict, quarantine: dict) -> bool:
    with tempfile.TemporaryDirectory(prefix="continuity-normalize-", dir=snapshot_file.parent) as room:
        directory = Path(room) / "out"
        snapshot_lib.normalize(snapshot_file, owner_map_file, directory)
        generated_quarantine = read_json(directory / "quarantine.json")
        packages = list(directory.glob("owner-*.json"))
        return (len(packages) == 1 and read_json(packages[0]) == package
                and generated_quarantine == quarantine)


def source_route_samples(url: str, package: dict, token: str | None = None) -> dict:
    """Execute the pinned v1 owner-query shapes against the read-only source."""
    source = (REPO / "raw/v1/gateway-main.py").read_text()
    names = ("JOURNAL_FACTS_QUERY", "JOURNAL_FEEDBACK_QUERY",
             "ACCEPTED_FACTS_QUERY", "PENDING_REVIEWS_QUERY")
    queries = {}
    for name in names:
        match = re.search(r"^" + name + r' = """(.*?)"""', source, re.M | re.S)
        if not match:
            raise ValueError(f"pinned source query missing: {name}")
        queries[name] = match.group(1)
    headers = {"Content-Type": "application/sparql-query", "Accept": "application/sparql-results+json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    rows = {}
    for name, query in queries.items():
        with urlopen(Request(url, query.encode(), headers, method="POST"), timeout=60) as response:
            payload = json.load(response)
        rows[name] = [{k: v["value"] for k, v in binding.items()}
                      for binding in payload["results"]["bindings"]]
    def category(resource: dict) -> str | None:
        return next((q["o"]["value"] for q in resource["value"].get("quads", [])
                     if q["p"]["value"] == "https://schema.org/additionalType"), None)
    expected_journal = sorted((r["value"]["subject"], r["value"]["text"], category(r))
                              for r in package["resources"]
                              if semantic_kind(r) == "journal" and r["value"].get("status") != "deleted")
    actual_journal = sorted((r["source"], r["label"], r["kind"])
                            for r in rows["JOURNAL_FACTS_QUERY"])
    expected_accepted = sum(semantic_kind(r) == "accepted" for r in package["resources"])
    expected_pending = sum(semantic_kind(r) == "candidate" and
                           r["value"].get("status") == "pending" for r in package["resources"])
    expected_feedback = sum(semantic_kind(r) == "review-action" for r in package["resources"])
    counts = {name: len(value) for name, value in rows.items()}
    return {"counts": counts,
            "journal-category-label-and-identity-parity": actual_journal == expected_journal,
            "accepted-count-parity": counts["ACCEPTED_FACTS_QUERY"] == expected_accepted,
            "pending-count-parity": counts["PENDING_REVIEWS_QUERY"] == expected_pending,
            "feedback-count-parity": counts["JOURNAL_FEEDBACK_QUERY"] == expected_feedback}


def semantic_kind(resource: dict) -> str | None:
    quads = resource["value"].get("quads", [])
    types = {q["o"]["value"] for q in quads if q["p"]["value"] ==
             "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"}
    has = lambda suffix: any(t.endswith(suffix) for t in types)
    graph = resource["value"].get("graph", "")
    if has("DailyJournal"):
        return "daily"
    if has("ReviewAction"):
        return "review-action"
    if graph.endswith(":accepted") and has("Claim"):
        return "accepted"
    if graph.endswith(":review") and has("Claim"):
        return "candidate"
    if (graph.endswith(":journal") and has("CreativeWork")
            and resource["value"].get("text") is not None
            and any(q["p"]["value"] == "https://schema.org/additionalType" for q in quads)):
        return "journal"
    return None


def dispositions(package: dict, quarantine: dict, approvals: dict | None = None) -> dict:
    approvals = approvals or {}
    mapped = package["resources"]
    pending = quarantine["resources"]
    ids = [r["legacy-id"] for r in mapped + pending]
    if len(ids) != len(set(ids)):
        raise ValueError("duplicate legacy resource")
    if quarantine["snapshot-id"] != package["snapshot-id"]:
        raise ValueError("quarantine snapshot mismatch")
    decisions = []
    for resource in pending:
        approval = approvals.get(resource["legacy-id"])
        if approval and approval.get("decision") == "exclude-metadata" and approval.get("owner-approved") \
                and approval.get("evidence-digest"):
            decisions.append({"legacy-id": resource["legacy-id"], "status": "excluded-metadata"})
        else:
            decisions.append({"legacy-id": resource["legacy-id"], "status": "quarantined"})
    return {"source-resources": len(ids), "mapped": len(mapped),
            "quarantined": sum(d["status"] == "quarantined" for d in decisions),
            "excluded-metadata": sum(d["status"] == "excluded-metadata" for d in decisions),
            "decisions": decisions}


def source_semantics(package: dict) -> dict:
    resources = package["resources"]
    accepted_subjects = {r["value"].get("subject") for r in resources if semantic_kind(r) == "accepted"}
    reviews = defaultdict(list)
    for resource in resources:
        if semantic_kind(resource) == "review-action":
            reviews[resource["value"].get("target")].append(resource)
    items, accounts = {}, {}
    for resource in resources:
        kind = semantic_kind(resource)
        value = resource["value"]
        legacy_id = resource["legacy-id"]
        if kind in {"journal", "candidate", "accepted"}:
            status = ("deleted" if value.get("status") == "deleted" else
                      "accepted" if kind == "accepted" or value.get("status") == "accepted"
                      or value.get("subject") in accepted_subjects else "pending")
            ordered = sorted(reviews[value.get("subject")],
                             key=lambda r: (r["value"].get("createdAt") or "", r["legacy-id"]))
            category = next((q["o"]["value"] for q in value.get("quads", [])
                             if q["p"]["value"] == "https://schema.org/additionalType"), None)
            items[legacy_id] = {"source-id": resource["source-id"],
                                "kind": "journal" if kind == "journal" else "assertion",
                                "legacy-category": category,
                                "current-text": value.get("text") or value.get("label"),
                                "status": status,
                                "reviews": [{"legacy-id": r["legacy-id"],
                                             "source-id": r["source-id"],
                                             "action": r["value"].get("action"),
                                             "created-at": r["value"].get("createdAt"),
                                             "target": r["value"].get("target")}
                                            for r in ordered]}
        elif kind == "daily":
            def property_value(predicate: str) -> str | None:
                return next((q["o"]["value"] for q in value.get("quads", [])
                             if q["p"]["value"] == predicate), None)
            accounts[legacy_id] = {"source-refs": value.get("sourceRefs"),
                                   "journal-date": property_value("urn:voice-agent:personal-knowledge:ontology#journalDate"),
                                   "time-zone": property_value("urn:voice-agent:personal-knowledge:ontology#timeZone"),
                                   "legacy-version-id": property_value("https://schema.org/identifier"),
                                   "story": value.get("text"), "status": "completed"}
    return {"items": items, "accounts": accounts,
            "accepted-ids": sorted(i for i, r in items.items()
                                   if r["kind"] == "assertion" and r["status"] == "accepted"),
            "active-journal-ids": sorted(i for i, r in items.items()
                                         if r["kind"] == "journal" and r["status"] != "deleted")}


def compare_shadow(source: dict, shadow: dict) -> list[dict]:
    differences = []
    actual_items = shadow["items"]
    for key in sorted(set(source["items"]) | set(actual_items)):
        expected = source["items"].get(key)
        actual = actual_items.get(key)
        if actual is not None:
            actual = {"source-id": actual.get("source-id"), "kind": actual.get("kind"),
                      "legacy-category": actual.get("legacy-category"),
                      "current-text": actual.get("current-text"), "status": actual.get("status"),
                      "reviews": [{"legacy-id": x.get("legacy-id"),
                                   "source-id": x.get("source-id"),
                                   "action": x.get("value", {}).get("action"),
                                   "created-at": x.get("value", {}).get("createdAt"),
                                   "target": x.get("value", {}).get("target")}
                                  for x in actual.get("reviews", [])]}
        if expected != actual:
            differences.append({"class": "item-or-review", "legacy-id": key,
                                "expected": expected, "actual": actual})
    for key in sorted(set(source["accounts"]) | set(shadow["accounts"])):
        expected = source["accounts"].get(key)
        raw = shadow["accounts"].get(key)
        actual = ({"source-refs": raw.get("source-refs"), "story": raw.get("story"),
                   "journal-date": raw.get("journal-date"), "time-zone": raw.get("time-zone"),
                   "legacy-version-id": raw.get("legacy-version-id"),
                   "status": raw.get("status")} if raw else None)
        if expected != actual:
            differences.append({"class": "daily-version", "legacy-id": key,
                                "expected": expected, "actual": actual})
    return differences


def without_graph_count(view: dict) -> dict:
    return {k: v for k, v in view.items() if k != "owner-concept-count"}


def qualify(snapshot: dict, package: dict, quarantine: dict, current: dict,
            grain: dict, approvals: dict | None = None,
            source_samples: dict | None = None,
            owner_map_verified: bool = False) -> tuple[dict, dict]:
    disposition = dispositions(package, quarantine, approvals)
    source = source_semantics(package)
    differences = compare_shadow(source, grain["shadow"])
    source_resource_count = len({(q["g"]["value"], q["s"]["value"]) for q in snapshot["quads"]})
    checks = {"source-current": snapshot_matches(snapshot, current),
              "package-identity": package_matches_snapshot(snapshot, package, quarantine),
              "owner-map-reproduced": owner_map_verified,
              "all-resources-accounted": disposition["source-resources"] == source_resource_count,
              "resource-reconcile": bool(grain["reconciliation"].get("matched?")),
              "semantic-parity": not differences,
              "replay": without_graph_count(grain["shadow"]) == without_graph_count(grain["after-restart"]),
              "graph-rebuild": grain["after-graph"] == grain["after-rebuild"],
              "rollback": grain["prior"] == grain["restored"]
                          and bool(grain["retained-after-rollback"].get("matched?")),
              "metadata-resolved": disposition["quarantined"] == 0}
    if source_samples is not None:
        checks["source-owner-query-parity"] = all(v for k, v in source_samples.items() if k != "counts")
    # The source only holds its current daily pages. Prior overwritten versions are unknowable.
    private = {"checks": checks, "disposition": disposition, "differences": differences,
               "semantic-source": source, "grain-rehearsal": grain,
               "source-route-samples": source_samples,
               "historical-daily-versions": "unobservable-in-prior-source"}
    public = {"snapshot-quad-count": snapshot["quadCount"],
              "source-quad-count": current["quadCount"],
              "resource-count": disposition["source-resources"],
              "mapped-count": disposition["mapped"],
              "quarantine-count": disposition["quarantined"],
              "excluded-metadata-count": disposition["excluded-metadata"],
              "quarantine-with-label-count": sum(bool(r["value"].get("label")) for r in quarantine["resources"]),
              "quarantine-with-source-count": sum(bool(r["value"].get("sourceRefs")) for r in quarantine["resources"]),
              "semantic-item-count": len(source["items"]),
              "active-journal-count": len(source["active-journal-ids"]),
              "deleted-journal-count": sum(v["kind"] == "journal" and v["status"] == "deleted"
                                           for v in source["items"].values()),
              "daily-current-version-count": len(source["accounts"]),
              "accepted-fact-count": len(source["accepted-ids"]),
              "review-action-count": sum(len(v["reviews"]) for v in source["items"].values()),
              "discrepancy-count": len(differences), "checks": checks,
              "shadow-qualified": all(checks.values()),
              "live-switch-approved": False,
              "historical-daily-versions": "unobservable-in-prior-source"}
    return private, public


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--snapshot", required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--quarantine", required=True)
    parser.add_argument("--owner-map", required=True)
    parser.add_argument("--storage-dir", required=True)
    parser.add_argument("--private-report", required=True)
    parser.add_argument("--url", default="http://127.0.0.1:7878/query")
    args = parser.parse_args()
    snap, package, quarantine = (read_json(Path(p)) for p in
                                 (args.snapshot, args.package, args.quarantine))
    current = live_source_identity(args.url, os.environ.get("AWP_OXIGRAPH_TOKEN"))
    if not snapshot_matches(snap, current):
        raise SystemExit("source drift: frozen snapshot does not match current source")
    if not package_matches_snapshot(snap, package, quarantine):
        raise SystemExit("normalized package does not account for the frozen source")
    if not owner_map_reproduces_package(Path(args.snapshot), Path(args.owner_map), package, quarantine):
        raise SystemExit("owner mapping does not reproduce the private normalized package")
    samples = source_route_samples(args.url, package, os.environ.get("AWP_OXIGRAPH_TOKEN"))
    storage = private_path(args.storage_dir)
    private_report = private_path(args.private_report)
    if storage.exists() or private_report.exists():
        raise SystemExit("shadow storage and report paths must be new")
    storage.mkdir(mode=0o700, parents=True)
    grain_output = private_report.with_name(private_report.stem + "-grain.json")
    command = ["clojure", "-J--add-opens=java.base/java.nio=ALL-UNNAMED",
               "-J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
               "-M", "-m", "awp-v2.knowledge.continuity-tool",
               str(storage), args.package, str(grain_output)]
    result = subprocess.run(command, cwd=REPO / "impl", capture_output=True, text=True, timeout=600)
    if result.returncode:
        diagnostics = private_report.with_name(private_report.stem + "-runner-error.log")
        diagnostics.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        with open(diagnostics, "x", encoding="utf-8",
                  opener=lambda name, flags: os.open(name, flags, 0o600)) as out:
            out.write(result.stderr)
            out.write(result.stdout)
        raise SystemExit("isolated Grain shadow failed; private operator diagnostics retained")
    grain = read_json(grain_output)
    current = live_source_identity(args.url, os.environ.get("AWP_OXIGRAPH_TOKEN"))
    private, public = qualify(snap, package, quarantine, current, grain,
                              source_samples=samples, owner_map_verified=True)
    private_write(private_report, private)
    print(json.dumps(public, sort_keys=True))


if __name__ == "__main__":
    main()
