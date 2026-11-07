"""Confirm a separate-device public media report against private v2 Grain state."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import httpx


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--grain-url", required=True)
    parser.add_argument("--grain-token-file", required=True, type=Path)
    parser.add_argument("--owner", required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text())
    if report.get("public_media") != "passed" or report.get("owner") != args.owner:
        raise AssertionError("report is not an owner public media pass")
    token = args.grain_token_file.read_text().strip()
    headers = {"Authorization": f"Bearer {token}", "X-AWP-Owner": args.owner}
    with httpx.Client(timeout=10) as client:
        selection = client.post(args.grain_url.rstrip("/") + "/internal/session/selection",
                                json={}, headers=headers)
        selection.raise_for_status()
        turn = client.post(args.grain_url.rstrip("/") + "/internal/session/turn",
                           json={"turn-id": report["turn_id"]}, headers=headers)
        turn.raise_for_status()
    selected = selection.json().get("id")
    settled = turn.json()
    if selected != report["conversation_id"] or settled.get("conversation-id") != selected:
        raise AssertionError("public selected conversation is not bound to private v2 Grain")
    if settled.get("status") != "completed" or not isinstance(settled.get("answer"), str):
        raise AssertionError("public turn is not settled in v2 Grain")
    if hashlib.sha256(settled["answer"].encode()).hexdigest() != report["answer_sha256"]:
        raise AssertionError("public answer differs from settled v2 Grain answer")
    print(json.dumps({"public_report": str(args.report), "grain_binding": "passed",
                      "turn_id": report["turn_id"], "conversation_id": selected}, indent=2))


if __name__ == "__main__":
    main()
