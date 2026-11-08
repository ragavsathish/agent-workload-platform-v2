"""Evidence ledger for one supervised local v2 release.

This ledger records observations; it never derives a journey pass from a
service health response or from another journey's result.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path

JOURNEYS = ("readiness", "voice", "task", "knowledge", "song", "incident",
            "restart", "recovery", "isolation")
GATES = ("external_media", "external_image", "private_migration",
         "model_quality", "pull_request")
OUTCOMES = {"exercised", "failed", "unavailable", "unqualified"}
GATE_OUTCOMES = {"open", "failed", "qualified"}
HEX64 = re.compile(r"[0-9a-f]{64}\Z")


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def new_report(*, version: str, source_revision: str, state_identity: str,
               owner: str) -> dict:
    if not version or not re.fullmatch(r"[0-9a-f]{40}", source_revision) or not state_identity or not owner:
        raise ValueError("release identity is incomplete")
    return {
        "schema": 1,
        "scope": "local",
        "version": version,
        "source_revision": source_revision,
        "state_identity": state_identity,
        "owner_digest": digest(owner.encode()),
        "started_at": utc_now(),
        "journeys": {name: {"outcome": "unqualified", "reason": "not yet exercised",
                            "evidence": {}} for name in JOURNEYS},
        "gates": {name: {"outcome": "open", "reason": "separate qualification required",
                          "evidence": {}} for name in GATES},
        "limitations": [],
    }


def record(report: dict, name: str, outcome: str, *, evidence: dict | None = None,
           reason: str = "") -> None:
    if name not in JOURNEYS or outcome not in OUTCOMES:
        raise ValueError("unknown journey or outcome")
    if outcome == "exercised" and not evidence:
        raise ValueError("an exercised journey needs direct evidence")
    if outcome != "exercised" and not reason:
        raise ValueError("a nonpassing journey needs a reason")
    report["journeys"][name] = {"outcome": outcome, "reason": reason,
                                 "evidence": evidence or {}, "observed_at": utc_now()}


def gate(report: dict, name: str, outcome: str, *, evidence: dict | None = None,
         reason: str = "") -> None:
    if name not in GATES or outcome not in GATE_OUTCOMES:
        raise ValueError("unknown gate or outcome")
    if outcome == "qualified" and not evidence:
        raise ValueError("an external gate needs its own evidence")
    if outcome != "qualified" and not reason:
        raise ValueError("an open or failed gate needs a reason")
    report["gates"][name] = {"outcome": outcome, "reason": reason,
                              "evidence": evidence or {}, "observed_at": utc_now()}


def validate(report: dict) -> None:
    if report.get("schema") != 1 or report.get("scope") != "local":
        raise ValueError("invalid local release report")
    if not re.fullmatch(r"[0-9a-f]{40}", str(report.get("source_revision", ""))):
        raise ValueError("invalid source revision")
    if not report.get("version") or not report.get("state_identity"):
        raise ValueError("release identity is missing")
    if not HEX64.fullmatch(str(report.get("owner_digest", ""))):
        raise ValueError("owner identity is not safely represented")
    if set(report.get("journeys", {})) != set(JOURNEYS) or set(report.get("gates", {})) != set(GATES):
        raise ValueError("release report omitted a journey or external gate")
    for name, item in report["journeys"].items():
        if item.get("outcome") not in OUTCOMES:
            raise ValueError(f"invalid {name} outcome")
        if item["outcome"] == "exercised" and not item.get("evidence"):
            raise ValueError(f"{name} has no direct evidence")
        if item["outcome"] != "exercised" and not item.get("reason"):
            raise ValueError(f"{name} has no reason")
    for name, item in report["gates"].items():
        if item.get("outcome") not in GATE_OUTCOMES:
            raise ValueError(f"invalid {name} gate")
        if item["outcome"] == "qualified" and not item.get("evidence"):
            raise ValueError(f"{name} has no independent evidence")
        if item["outcome"] != "qualified" and not item.get("reason"):
            raise ValueError(f"{name} has no reason")
    serialized = json.dumps(report, sort_keys=True)
    if re.search(r"(?i)(authorization|bearer |secret[_-]?key|api[_-]?token|password)[^\n]{0,40}[:=]", serialized):
        raise ValueError("report contains a credential-looking field")


def save_report(path: str | Path, report: dict) -> None:
    validate(report)
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = target.with_name(target.name + f".{os.getpid()}.tmp")
    temporary.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    temporary.chmod(0o600)
    temporary.replace(target)


def load_report(path: str | Path) -> dict:
    report = json.loads(Path(path).read_text())
    validate(report)
    return report
