#!/usr/bin/env python3
"""Export and normalize a private Oxigraph snapshot. Output must stay outside this repository."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from collections import Counter, defaultdict
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen

QUERY = "SELECT ?g ?s ?p ?o WHERE { GRAPH ?g { ?s ?p ?o } } ORDER BY ?g ?s ?p ?o"
IDENTIFIER = "https://schema.org/identifier"
SOURCE_LINKS = {
    "https://schema.org/isBasedOn",
    "http://www.w3.org/ns/prov#wasDerivedFrom",
    "http://www.w3.org/ns/prov#used",
}
REPO = Path(__file__).resolve().parents[2]


def canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def digest(value: object) -> str:
    return hashlib.sha256(canonical(value)).hexdigest()


def private_path(value: str) -> Path:
    path = Path(value).expanduser().resolve()
    if path == REPO or REPO in path.parents:
        raise SystemExit("private migration output must be outside the repository")
    return path


def write_private(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as output:
        output.write(canonical(value) + b"\n")


def export(url: str, output: Path, token: str | None) -> dict:
    parsed = urlparse(url)
    if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost"} or parsed.path != "/query":
        raise SystemExit("Oxigraph query URL must be loopback /query")
    headers = {"Content-Type": "application/sparql-query", "Accept": "application/sparql-results+json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    with urlopen(Request(url, QUERY.encode(), headers, method="POST"), timeout=60) as response:
        payload = json.load(response)
    rows = []
    for binding in payload["results"]["bindings"]:
        rows.append({key: binding[key] for key in ("g", "s", "p", "o")})
    rows.sort(key=lambda row: canonical(row))
    counts = dict(sorted(Counter(row["g"]["value"] for row in rows).items()))
    snapshot = {"format": "awp-v1-oxigraph-quads-v1", "query": QUERY, "quadCount": len(rows),
                "graphCounts": counts, "quadDigest": digest(rows), "quads": rows}
    write_private(output, snapshot)
    return {key: snapshot[key] for key in ("format", "quadCount", "graphCounts", "quadDigest")}


def normalize(source: Path, owner_map_path: Path, output_dir: Path) -> dict:
    snapshot = json.loads(source.read_text())
    quads = snapshot["quads"]
    if digest(quads) != snapshot["quadDigest"] or len(quads) != snapshot["quadCount"]:
        raise SystemExit("source snapshot digest or count mismatch")
    owner_map = json.loads(owner_map_path.read_text())
    if not isinstance(owner_map, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in owner_map.items()):
        raise SystemExit("owner map must be source reference to owner string")
    by_subject = defaultdict(list)
    for row in quads:
        by_subject[row["s"]["value"]].append(row)
    inbound = defaultdict(list)
    for row in quads:
        if row["o"]["type"] == "uri" and row["p"]["value"] in {
            "https://schema.org/isPartOf", "http://www.w3.org/ns/prov#wasGeneratedBy",
            "https://schema.org/sender"}:
            inbound[row["o"]["value"]].append(row["s"]["value"])
    def refs(subject: str, visited: frozenset[str] = frozenset()) -> set[str]:
        if subject in visited:
            return set()
        rows = by_subject.get(subject, [])
        direct = {row["o"]["value"] for row in rows if row["p"]["value"] == IDENTIFIER
                  and row["o"]["value"] in owner_map}
        linked = set()
        for row in rows:
            if row["p"]["value"] in SOURCE_LINKS and row["o"]["type"] == "uri":
                linked.update(refs(row["o"]["value"], visited | {subject}))
        for incoming in inbound.get(subject, []):
            linked.update(refs(incoming, visited | {subject}))
        return direct | linked
    def object_value(rows, predicate):
        return next((row["o"]["value"] for row in rows if row["p"]["value"] == predicate), None)
    def kind_for(graph, rows):
        types = {row["o"]["value"] for row in rows if row["p"]["value"] == "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"}
        if any(value.endswith("DailyJournal") for value in types):
            return "daily"
        suffix = graph.rsplit(":", 1)[-1]
        if suffix == "journal" and any(value.endswith("JournalEntry") or value.endswith("CreativeWork") for value in types):
            return "journal"
        if suffix == "review" and any(value.endswith("Claim") for value in types):
            return "candidate"
        if suffix == "accepted":
            return "accepted"
        if suffix.endswith("feedback"):
            return "review-action"
        if suffix == "assertion-classification":
            return "classification"
        return suffix
    grouped = defaultdict(list)
    for row in quads:
        grouped[(row["g"]["value"], row["s"]["value"])].append(row)
    owners = defaultdict(list)
    quarantined = []
    for (graph, subject), rows in sorted(grouped.items()):
        source_refs = sorted(refs(subject))
        mapped_owners = {owner_map[ref] for ref in source_refs}
        owner_proof = None
        if not source_refs and ":owner:" in subject:
            owner_proof = next((candidate for candidate in set(owner_map.values())
                                if subject.endswith(hashlib.sha256(candidate.lower().encode()).hexdigest())), None)
            if owner_proof:
                mapped_owners.add(owner_proof)
        source_id = source_refs[0] if len(source_refs) == 1 else f"bundle:{digest(source_refs)}" if source_refs else f"owner-identity:{hashlib.sha256(owner_proof.lower().encode()).hexdigest()}" if owner_proof else None
        record = {"legacy-id": f"{graph}|{subject}", "source-id": source_id,
                  "kind": kind_for(graph, rows), "value": {
                      "graph": graph, "subject": subject, "sourceRefs": source_refs,
                      "text": object_value(rows, "https://schema.org/text"),
                      "label": object_value(rows, "https://schema.org/name"),
                      "status": object_value(rows, "urn:awp:digital-twin:predicate:status"),
                      "target": object_value(rows, "https://schema.org/isBasedOn"),
                      "action": object_value(rows, "https://schema.org/actionStatus"),
                      "createdAt": object_value(rows, "https://schema.org/dateCreated"),
                      "quads": rows}}
        if len(mapped_owners) == 1 and source_id:
            owner = next(iter(mapped_owners))
            owners[owner].append({**record, "owner": owner})
        else:
            quarantined.append({**record, "owner": None, "reason": "owner-or-source-ambiguous"})
    snapshot_id = snapshot["quadDigest"]
    if REPO == output_dir or REPO in output_dir.parents:
        raise SystemExit("private migration output must be outside the repository")
    output_dir.mkdir(parents=True, exist_ok=True)
    for owner, resources in owners.items():
        name = hashlib.sha256(owner.encode()).hexdigest()[:16]
        package = {"snapshot-id": snapshot_id, "digest": digest(resources), "owner": owner, "resources": resources,
                   "quarantine-count": len(quarantined), "source-quad-digest": snapshot["quadDigest"]}
        write_private(output_dir / f"owner-{name}.json", package)
    write_private(output_dir / "quarantine.json", {"snapshot-id": snapshot_id, "resources": quarantined})
    report = {"snapshotId": snapshot_id, "quadCount": len(quads), "resourceCount": len(grouped),
              "mappedResources": sum(map(len, owners.values())), "quarantinedResources": len(quarantined),
              "owners": len(owners), "graphCounts": snapshot["graphCounts"]}
    write_private(output_dir / "report.json", report)
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    exp = sub.add_parser("export")
    exp.add_argument("--url", required=True)
    exp.add_argument("--output", required=True)
    exp.add_argument("--token-env", default="AWP_OXIGRAPH_TOKEN")
    norm = sub.add_parser("normalize")
    norm.add_argument("--snapshot", required=True)
    norm.add_argument("--owner-map", required=True)
    norm.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    if args.command == "export":
        report = export(args.url, private_path(args.output), os.environ.get(args.token_env))
    else:
        report = normalize(Path(args.snapshot), Path(args.owner_map), private_path(args.output_dir))
    print(json.dumps(report, sort_keys=True))


if __name__ == "__main__":
    main()
