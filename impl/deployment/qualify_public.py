"""Run a public media gate from an owner device joined to the tailnet."""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "qualification"))
from deployment import MediaProof
from voice_media import run_voice_session  # type: ignore


def tailnet_peer(origin: str) -> str:
    if not shutil.which("tailscale"):
        raise RuntimeError("Tailscale CLI is required on the separate owner device")
    status = json.loads(subprocess.check_output(["tailscale", "status", "--json"], timeout=10))
    self_node = status.get("Self") or {}
    local = str(self_node.get("DNSName", "")).rstrip(".")
    target = urlsplit(origin).hostname or ""
    if not status.get("BackendState") == "Running" or not local or local == target:
        raise RuntimeError("probe must run on a distinct, connected tailnet device")
    return local


def public_get(client: httpx.Client, origin: str, path: str) -> dict:
    response = client.get(origin + path, headers={"Origin": origin})
    response.raise_for_status()
    return response.json()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--origin", required=True)
    parser.add_argument("--owner", required=True)
    parser.add_argument("--audio-wav", type=Path)
    parser.add_argument("--expect-denied", action="store_true")
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--utterance", default="Describe this selected conversation briefly.")
    args = parser.parse_args()
    origin = args.origin.rstrip("/")
    if urlsplit(origin).scheme != "https":
        parser.error("public origin must use HTTPS")
    peer = tailnet_peer(origin)
    if args.expect_denied:
        try:
            with httpx.Client(timeout=10) as client:
                response = client.get(origin + "/api/pi-sessions",
                                      headers={"Origin": origin, "Tailscale-User-Login": args.owner})
            if response.status_code not in (401, 403, 404):
                raise AssertionError("foreign tailnet peer reached owner data")
        except (httpx.ConnectError, httpx.ConnectTimeout):
            pass  # Tailnet policy may deny the connection before HTTP.
        report = {"origin": origin, "peer_node": peer, "foreign_access": "denied"}
        args.report.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        args.report.write_text(json.dumps(report, indent=2) + "\n")
        args.report.chmod(0o600)
        print(json.dumps(report, indent=2))
        return
    if not args.audio_wav:
        parser.error("--audio-wav is required for the owner media journey")
    with httpx.Client(timeout=30) as client:
        catalog = public_get(client, origin, "/api/pi-sessions")
        active = [entry["id"] for entry in catalog.get("sessions", []) if entry.get("active")]
        if len(active) != 1:
            raise AssertionError("public owner has no unique selected conversation")
        before = public_get(client, origin, f"/api/pi-sessions/{active[0]}")
    result = asyncio.run(run_voice_session(
        [args.utterance], args.owner, origin, 180,
        edge_url=origin, audio_wav=str(args.audio_wav), send_identity_header=False))[0]
    ice = result.get("ice") or {}
    proof = MediaProof(origin, str(ice.get("local_address", "")),
                       str(ice.get("remote_address", "")), str(ice.get("remote_type", "")),
                       1 if result.get("transcriptions") else 0,
                       int(result.get("output_bytes", 0)), "pending", "pending")
    if not proof.qualifies() or not result.get("response_completed"):
        raise AssertionError("public media path did not carry a completed spoken turn")
    with httpx.Client(timeout=30) as client:
        after = public_get(client, origin, f"/api/pi-sessions/{active[0]}")
    old = {entry.get("id") for entry in before.get("entries", [])}
    new_users = [entry for entry in after.get("entries", [])
                 if entry.get("role") == "user" and entry.get("id") not in old]
    if len(new_users) != 1:
        raise AssertionError("public selected history did not gain exactly one user turn")
    turn_id = str(new_users[0]["id"]).removesuffix("-user")
    answer = next((entry.get("text") for entry in after.get("entries", [])
                   if entry.get("id") == f"{turn_id}-assistant"), None)
    if not answer:
        raise AssertionError("public selected history has no matching assistant answer")
    report = {"origin": origin, "peer_node": peer, "owner": args.owner,
              "ice": ice, "turn_id": turn_id, "conversation_id": active[0],
              "response_audio_bytes": result["output_bytes"],
              "answer_sha256": hashlib.sha256(answer.encode()).hexdigest(),
              "public_media": "passed", "grain_private_comparison": "requires host-side check"}
    args.report.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    args.report.chmod(0o600)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
