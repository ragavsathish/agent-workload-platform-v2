"""Synthetic SmallWebRTC voice probe for the deployed edge container."""

from __future__ import annotations

import asyncio
import hashlib
import json
import os
import subprocess
import sys
import wave
from dataclasses import dataclass, replace
from fractions import Fraction
from pathlib import Path
from typing import Awaitable, Callable

import httpx
import numpy as np
from aiortc import (
    MediaStreamError,
    MediaStreamTrack,
    RTCPeerConnection,
    RTCSessionDescription,
)
from av import AudioFrame

sys.path.insert(0, str(Path(__file__).resolve().parents[1]
                       / "runtime/products/voice-agent/apps/edge"))
from presentation_response import parse_presentation_response  # noqa: E402

class SyntheticMicrophone(MediaStreamTrack):
    kind = "audio"

    def __init__(self) -> None:
        super().__init__()
        self._pcm = b""
        self._offset = 0
        self._pts = 0
        self._drained = asyncio.Event()
        self._drained.set()

    def play(self, pcm: bytes) -> None:
        if not self._drained.is_set():
            raise RuntimeError("synthetic microphone is already playing")
        self._pcm = pcm
        self._offset = 0
        self._drained.clear()

    async def wait_until_drained(self, timeout: float) -> None:
        await asyncio.wait_for(self._drained.wait(), timeout=timeout)

    async def recv(self) -> AudioFrame:
        samples = 320  # 20 ms at 16 kHz
        if self._offset < len(self._pcm):
            end = self._offset + samples * 2
            payload = self._pcm[self._offset : end]
            self._offset = end
            if self._offset >= len(self._pcm):
                self._drained.set()
        else:
            # Keep RTP flowing between turns and while the welcome turn runs.
            payload = b""
        payload += b"\0" * max(0, samples * 2 - len(payload))
        frame = AudioFrame(format="s16", layout="mono", samples=samples)
        frame.planes[0].update(payload)
        frame.sample_rate = 16_000
        frame.pts = self._pts
        frame.time_base = Fraction(1, 16_000)
        self._pts += samples
        await asyncio.sleep(0.02)
        return frame


def resample_24k_to_16k(pcm: bytes) -> bytes:
    source = np.frombuffer(pcm, dtype=np.int16).astype(np.float32)
    target_size = round(source.size * 2 / 3)
    target = np.interp(
        np.linspace(0, source.size - 1, target_size),
        np.arange(source.size),
        source,
    )
    return np.clip(target, -32768, 32767).astype(np.int16).tobytes()


def audible_sample_bytes(samples: np.ndarray) -> int:
    """Count non-silent PCM equivalents; RTP silence must not satisfy audio checks."""
    threshold = 0.015 if np.issubdtype(samples.dtype, np.floating) else 500
    return 2 * int(np.count_nonzero(np.abs(samples.astype(np.float32)) > threshold))


async def synthesize(utterance: str, broker_url: str, token: str) -> bytes:
    async with httpx.AsyncClient(timeout=300) as client:
        response = await client.post(
            f"{broker_url.rstrip('/')}/v1/audio/speech",
            headers={"Authorization": f"Bearer {token}"},
            json={
                "model": "kokoro",
                "input": utterance,
                "voice": "af_heart",
                "response_format": "pcm",
            },
        )
        response.raise_for_status()
        return resample_24k_to_16k(response.content)


async def run_voice_session(
    utterances: list[str], identity: str, origin: str, timeout: float,
    *, edge_url: str = "http://127.0.0.1:7860", broker_url: str = "",
    broker_token: str = "", audio_wav: str = "", local_ingress: bool = False,
    before_input: Callable[[], Awaitable[None]] | None = None,
    after_input: Callable[[], Awaitable[None]] | None = None,
) -> list[dict[str, object]]:
    if audio_wav:
        with wave.open(audio_wav, "rb") as source:
            if (source.getnchannels(), source.getsampwidth(), source.getframerate()) != (1, 2, 16000):
                raise ValueError("audio WAV must be mono PCM16 at 16 kHz")
            audio = source.readframes(source.getnframes())
        pcms = [audio for _ in utterances]
    elif utterances:
        if not broker_url or not broker_token:
            raise ValueError("broker URL and token are required to synthesize the utterance")
        pcms = [await synthesize(utterance, broker_url, broker_token) for utterance in utterances]
    else:
        pcms = []
    peer = RTCPeerConnection()
    microphone = SyntheticMicrophone()
    peer.addTrack(microphone)
    channel = peer.createDataChannel("data")
    events: list[str] = []
    server_messages: list[dict[str, object]] = []
    transcriptions: list[dict[str, object]] = []
    output_bytes = 0
    ready = asyncio.Event()
    welcome_done = asyncio.Event()
    response_done = asyncio.Event()
    awaiting_response = False

    @channel.on("open")
    def on_open() -> None:
        channel.send(
            json.dumps(
                {
                    "label": "rtvi-ai",
                    "type": "client-ready",
                    "id": "smallwebrtc-probe",
                    "data": {"version": "1.0.0", "about": {"library": "probe"}},
                }
            )
        )
        ready.set()

    @channel.on("message")
    def on_message(raw: bytes | str) -> None:
        nonlocal awaiting_response
        try:
            payload = json.loads(raw if isinstance(raw, str) else raw.decode())
            event_type = str(payload.get("type", ""))
            events.append(event_type)
            if event_type in {"user-transcription", "user-transcript"}:
                transcriptions.append(payload)
            if event_type == "server-message":
                message = payload.get("data")
                if isinstance(message, dict):
                    nested = message.get("data")
                    server_messages.append(
                        nested if isinstance(nested, dict) else message
                    )
            if event_type == "bot-stopped-speaking" and ready.is_set():
                if awaiting_response:
                    response_done.set()
                else:
                    welcome_done.set()
        except (AttributeError, json.JSONDecodeError, UnicodeDecodeError):
            return

    @peer.on("track")
    def on_track(track: MediaStreamTrack) -> None:
        async def consume() -> None:
            nonlocal output_bytes
            while True:
                try:
                    frame = await track.recv()
                except MediaStreamError:
                    return
                if isinstance(frame, AudioFrame):
                    output_bytes += audible_sample_bytes(frame.to_ndarray())

        asyncio.create_task(consume())

    try:
        offer = await peer.createOffer()
        await peer.setLocalDescription(offer)
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(
                f"{edge_url.rstrip('/')}/api/offer",
                headers={
                    "Origin": origin,
                    "tailscale-user-login": identity,
                    **({"x-voice-local-ingress": "1"} if local_ingress else {}),
                },
                json={
                    "sdp": peer.localDescription.sdp,
                    "type": peer.localDescription.type,
                    "request_data": {"transport": "webrtc"},
                },
            )
            response.raise_for_status()
            answer = response.json()
        await peer.setRemoteDescription(
            RTCSessionDescription(sdp=answer["sdp"], type=answer["type"])
        )

        await asyncio.wait_for(ready.wait(), timeout=20)
        # The server starts a welcome turn after client-ready. Do not let the
        # synthetic microphone interrupt it: browser users naturally wait for this
        # cue, and the black-box probe must exercise the same turn boundary.
        await asyncio.wait_for(welcome_done.wait(), timeout=timeout)
        if before_input is not None:
            await before_input()
        results: list[dict[str, object]] = []
        for utterance, pcm in zip(utterances, pcms, strict=True):
            event_start = len(events)
            message_start = len(server_messages)
            transcription_start = len(transcriptions)
            output_start = output_bytes
            response_done.clear()
            awaiting_response = True
            microphone.play(pcm)
            await microphone.wait_until_drained(timeout)
            if after_input is not None:
                await after_input()
                return results
            # Give VAD one second of trailing silence after each utterance.
            await asyncio.sleep(1.0)
            try:
                await asyncio.wait_for(response_done.wait(), timeout=timeout)
            except TimeoutError:
                pass
            results.append({
                "utterance": utterance,
                "events": events[event_start:],
                "server_messages": server_messages[message_start:],
                "transcriptions": transcriptions[transcription_start:],
                "output_bytes": output_bytes - output_start,
                "response_completed": response_done.is_set(),
            })
            awaiting_response = False
    finally:
        await peer.close()

    return results


def assert_result(result: dict[str, object]) -> None:
    if not result["response_completed"] or not result["output_bytes"]:
        raise AssertionError("Voice response or remote audio did not complete")


@dataclass(frozen=True)
class Qualification:
    """Opt-in checks against a running deployment; never substitutes speech services."""

    edge_url: str
    grain_url: str
    grain_token: str
    origin: str
    identity: str
    broker_url: str
    broker_token: str
    utterance: str
    timeout: float
    local_ingress: bool = False
    audio_wav: str = ""
    stt_outage_file: str = ""
    hold_turn_file: str = ""
    task_id: str = ""
    task_evidence_file: str = ""
    dispatch_evidence_file: str = ""
    restart_argv: tuple[str, ...] = ()

    @classmethod
    def from_environment(cls, env: dict[str, str]) -> "Qualification":
        required = ["VOICE_EDGE_URL", "VOICE_GRAIN_URL", "VOICE_GRAIN_TOKEN",
                    "VOICE_ORIGIN", "VOICE_OWNER_IDENTITY", "VOICE_UTTERANCE"]
        missing = [name for name in required if not env.get(name)]
        if not env.get("VOICE_AUDIO_WAV"):
            missing += [name for name in ("VOICE_BROKER_URL", "VOICE_BROKER_TOKEN") if not env.get(name)]
        if missing:
            raise RuntimeError("deployed voice qualification needs: " + ", ".join(missing))
        return cls(
            edge_url=env["VOICE_EDGE_URL"].rstrip("/"),
            grain_url=env["VOICE_GRAIN_URL"].rstrip("/"),
            grain_token=env["VOICE_GRAIN_TOKEN"],
            origin=env["VOICE_ORIGIN"],
            identity=env["VOICE_OWNER_IDENTITY"],
            broker_url=env.get("VOICE_BROKER_URL", ""),
            broker_token=env.get("VOICE_BROKER_TOKEN", ""),
            utterance=env["VOICE_UTTERANCE"],
            timeout=float(env.get("VOICE_TIMEOUT", "120")),
            local_ingress=env.get("VOICE_LOCAL_INGRESS") == "1",
            audio_wav=env.get("VOICE_AUDIO_WAV", ""),
            stt_outage_file=env.get("VOICE_STT_OUTAGE_FILE", ""),
            hold_turn_file=env.get("VOICE_HOLD_TURN_FILE", ""),
            task_id=env.get("VOICE_TASK_ID", ""),
            task_evidence_file=env.get("VOICE_TASK_EVIDENCE_FILE", ""),
            dispatch_evidence_file=env.get("VOICE_DISPATCH_EVIDENCE_FILE", ""),
            restart_argv=tuple(json.loads(env.get("VOICE_RESTART_ARGV_JSON", "[]"))),
        )

    def headers(self) -> dict[str, str]:
        return {"Origin": self.origin, "tailscale-user-login": self.identity,
                **({"x-voice-local-ingress": "1"} if self.local_ingress else {})}

    def request(self, method: str, path: str, **kwargs) -> httpx.Response:
        with httpx.Client(timeout=30) as client:
            response = client.request(method, self.edge_url + path,
                                      headers={**self.headers(), **kwargs.pop("headers", {})}, **kwargs)
        response.raise_for_status()
        return response

    def media(self, utterances: list[str] | None = None,
              before_input: Callable[[], Awaitable[None]] | None = None,
              after_input: Callable[[], Awaitable[None]] | None = None) -> list[dict[str, object]]:
        return asyncio.run(run_voice_session(
            [self.utterance] if utterances is None else utterances,
            self.identity, self.origin, self.timeout,
            edge_url=self.edge_url, broker_url=self.broker_url,
            broker_token=self.broker_token, audio_wav=self.audio_wav,
            local_ingress=self.local_ingress, before_input=before_input,
            after_input=after_input,
        ))

    def grain_turn(self, turn_id: str) -> dict[str, object]:
        with httpx.Client(timeout=10) as client:
            response = client.post(
                self.grain_url + "/internal/session/turn",
                headers={"authorization": f"Bearer {self.grain_token}",
                         "x-awp-owner": self.identity, "content-type": "application/json"},
                json={"turn-id": turn_id},
            )
        response.raise_for_status()
        return response.json()

    def grain_routing(self) -> dict[str, object]:
        with httpx.Client(timeout=10) as client:
            response = client.post(
                self.grain_url + "/internal/session/routing",
                headers={"authorization": f"Bearer {self.grain_token}",
                         "x-awp-owner": self.identity, "content-type": "application/json"},
                json={},
            )
        response.raise_for_status()
        return response.json()

    def dispatch_count(self, turn_id: str) -> int:
        if not self.dispatch_evidence_file:
            raise RuntimeError("exact dispatch proof requires VOICE_DISPATCH_EVIDENCE_FILE")
        evidence = Path(self.dispatch_evidence_file)
        if not evidence.exists():
            return 0
        return sum(json.loads(line).get("turnId") == turn_id
                   for line in evidence.read_text().splitlines())

    def grain_selection(self) -> str:
        with httpx.Client(timeout=10) as client:
            response = client.post(
                self.grain_url + "/internal/session/selection",
                headers={"authorization": f"Bearer {self.grain_token}",
                         "x-awp-owner": self.identity, "content-type": "application/json"},
                json={},
            )
        response.raise_for_status()
        selected = response.json().get("id")
        if not isinstance(selected, str) or not selected:
            raise AssertionError("v2 Grain bridge returned no selected identity")
        return selected

    def assert_v2_binding(self) -> str:
        selected = self.grain_selection()
        catalog = self.request("GET", "/api/pi-sessions").json()
        active = [item.get("id") for item in catalog.get("sessions", [])
                  if item.get("active") is True]
        if active != [selected]:
            raise AssertionError("public owner session is not bound to v2 Grain selection")
        return selected

    def voice_001(self) -> None:
        self.assert_v2_binding()
        session = self.request("POST", "/api/pi-sessions/new",
                               headers={"idempotency-key": "voice-001-" + os.urandom(12).hex()}).json()
        session_id = session["id"]
        if self.grain_selection() != session_id:
            raise AssertionError("public fresh selection is not bound to v2 Grain")
        before = self.request("GET", f"/api/pi-sessions/{session_id}").json()
        previous_ids: set[object] = set()

        async def snapshot_before_speech() -> None:
            async with httpx.AsyncClient(timeout=10) as client:
                response = await client.get(self.edge_url + "/api/pi-transcript",
                                            headers=self.headers())
                response.raise_for_status()
                previous_ids.update(entry.get("id") for entry in response.json().get("entries", []))

        result = self.media(before_input=snapshot_before_speech)[0]
        assert_result(result)
        if not result["transcriptions"]:
            raise AssertionError("no recognized utterance was observed on the live channel")
        after = self.request("GET", f"/api/pi-sessions/{session_id}").json()
        if after == before:
            raise AssertionError("selected public history did not change after live media")
        entries = after.get("entries", [])
        new_users = [entry for entry in entries if entry.get("role") == "user"
                     and entry.get("id") not in previous_ids
                     and str(entry.get("id", "")).endswith("-user")]
        if len(new_users) != 1:
            raise AssertionError(f"expected one accepted spoken turn, found {len(new_users)}")
        turn = self.grain_turn(str(new_users[0]["id"])[:-5])
        if turn.get("status") != "completed" or turn.get("conversation-id") != session_id:
            raise AssertionError("accepted spoken turn did not complete in selected Grain conversation")
        if self.dispatch_count(str(new_users[0]["id"])[:-5]) != 1:
            raise AssertionError("selected spoken turn was not dispatched exactly once")
        if not any(entry.get("role") == "assistant" and entry.get("text") for entry in entries):
            raise AssertionError("selected history has no safe assistant answer")
        answer = turn.get("answer")
        assistant = next((entry for entry in entries
                          if entry.get("id") == str(new_users[0]["id"])[:-5] + "-assistant"), None)
        if not isinstance(answer, str) or not assistant or assistant.get("text") != answer:
            raise AssertionError("selected public answer differs from settled Grain answer")
        spoken = parse_presentation_response(answer).spoken
        if not any(message.get("type") == "mcp-app" and message.get("spoken") == spoken
                   for message in result["server_messages"]):
            raise AssertionError("spoken media presentation differs from selected safe answer")
        active = self.request("GET", "/api/pi-transcript").json()
        if active.get("entries") != entries:
            raise AssertionError("active transcript differs from selected history")
        if self.grain_selection() != session_id:
            raise AssertionError("live media changed the v2 Grain selection")

    def voice_002(self) -> None:
        self.assert_v2_binding()
        if not self.restart_argv:
            raise RuntimeError("voice-002 requires VOICE_RESTART_ARGV_JSON for a controlled fresh service restart")
        session = self.request("POST", "/api/pi-sessions/new",
                               headers={"idempotency-key": "voice-002-" + os.urandom(12).hex()}).json()
        session_id = session["id"]
        if self.grain_selection() != session_id:
            raise AssertionError("public fresh selection is not bound to v2 Grain")
        result = self.media()[0]
        assert_result(result)
        before = self.request("GET", f"/api/pi-sessions/{session_id}").json()
        if not before.get("entries"):
            raise AssertionError("spoken turn has no saved public history before restart")
        turn_ids = [str(entry["id"])[:-5] for entry in before.get("entries", [])
                    if entry.get("role") == "user" and str(entry.get("id", "")).endswith("-user")]
        dispatch_counts = {turn_id: self.dispatch_count(turn_id) for turn_id in turn_ids}
        subprocess.run(self.restart_argv, check=True, timeout=120)
        for _ in range(60):
            try:
                after = self.request("GET", f"/api/pi-sessions/{session_id}").json()
                if after.get("entries") == before.get("entries"):
                    self.request("POST", f"/api/pi-sessions/{session_id}/resume")
                    resumed = self.request("GET", "/api/pi-transcript").json()
                    if resumed.get("entries") == before.get("entries"):
                        if any(self.dispatch_count(turn_id) != count
                               for turn_id, count in dispatch_counts.items()):
                            raise AssertionError("restart redispatched a completed spoken turn")
                        if self.grain_selection() != session_id:
                            raise AssertionError("resumed selection disagrees with v2 Grain")
                        return
            except (httpx.HTTPError, KeyError):
                pass
            import time
            time.sleep(1)
        raise AssertionError("selected spoken history did not survive a fresh service restart")

    def voice_003(self) -> None:
        self.assert_v2_binding()
        import time

        if not self.hold_turn_file:
            raise RuntimeError("voice-003 requires VOICE_HOLD_TURN_FILE on an isolated Pi service")

        session = self.request("POST", "/api/pi-sessions/new",
                               headers={"idempotency-key": "voice-003-" + os.urandom(12).hex()}).json()
        session_id = session["id"]
        prior_ids: set[object] = set()
        interrupted: list[str] = []

        async def snapshot_before_speech() -> None:
            async with httpx.AsyncClient(timeout=10) as client:
                response = await client.get(self.edge_url + "/api/pi-transcript",
                                            headers=self.headers())
                response.raise_for_status()
                prior_ids.update(entry.get("id") for entry in response.json().get("entries", []))
            Path(self.hold_turn_file).write_text("hold\n")

        async def disconnect_on_accepted() -> None:
            async with httpx.AsyncClient(timeout=5) as client:
                for _ in range(120):
                    response = await client.get(self.edge_url + "/api/pi-transcript",
                                                headers=self.headers())
                    response.raise_for_status()
                    users = [entry for entry in response.json().get("entries", [])
                             if entry.get("role") == "user"
                             and entry.get("id") not in prior_ids
                             and str(entry.get("id", "")).endswith("-user")]
                    if users:
                        turn_id = str(users[-1]["id"])[:-5]
                        turn = self.grain_turn(turn_id)
                        if turn.get("status") != "accepted":
                            raise AssertionError("turn settled before controlled disconnect")
                        interrupted.append(turn_id)
                        return
                    await asyncio.sleep(0.1)
            raise AssertionError("spoken input did not reach an accepted Grain turn")

        utterance = os.getenv("VOICE_INTERRUPT_UTTERANCE", "Explain the detailed steps and tradeoffs for a complex project.")
        try:
            self.media([utterance], before_input=snapshot_before_speech,
                       after_input=disconnect_on_accepted)
        finally:
            Path(self.hold_turn_file).unlink(missing_ok=True)
        if len(interrupted) != 1:
            raise AssertionError("controlled disconnect did not capture one turn")
        turn_id = interrupted[0]
        for _ in range(60):
            turn = self.grain_turn(turn_id)
            if turn.get("status") != "accepted":
                break
            time.sleep(0.5)
        else:
            raise AssertionError("disconnected turn remained accepted")
        if turn.get("status") not in {"completed", "uncertain"}:
            raise AssertionError("disconnected turn has no inspectable outcome")
        if turn.get("stop-requested?") is not True:
            raise AssertionError("disconnected destination received no interruption request")
        if turn.get("conversation-id") != session_id or turn.get("task-id") is not None:
            raise AssertionError("disconnected turn lost selected destination")
        after_disconnect = self.request("GET", f"/api/pi-sessions/{session_id}").json()
        utterance_entry = next((entry for entry in after_disconnect.get("entries", [])
                                if entry.get("id") == turn_id + "-user"), None)
        if utterance_entry is None:
            raise AssertionError("interrupted turn is absent from public history")
        utterance_text = utterance_entry.get("text")
        prior_matching = sum(entry.get("role") == "user" and entry.get("text") == utterance_text
                             for entry in after_disconnect.get("entries", []))
        self.media([])  # Reconnect the media peer without replaying input.
        after_reconnect = self.request("GET", f"/api/pi-sessions/{session_id}").json()
        reconnect_user_ids = [entry.get("id") for entry in after_reconnect.get("entries", [])
                              if entry.get("role") == "user"]
        after_matching = sum(entry.get("role") == "user" and entry.get("text") == utterance_text
                             for entry in after_reconnect.get("entries", []))
        if reconnect_user_ids.count(turn_id + "-user") != 1 or after_matching != prior_matching:
            raise AssertionError("reconnect redispatched the interrupted spoken utterance")
        if self.grain_turn(turn_id).get("status") != turn.get("status"):
            raise AssertionError("reconnect changed interrupted turn outcome")
        if self.dispatch_count(turn_id) != 1:
            raise AssertionError("interrupted turn was dispatched more than once")
        if self.grain_selection() != session_id:
            raise AssertionError("reconnect changed selected Grain conversation")

    def voice_004(self) -> None:
        self.assert_v2_binding()
        if not self.task_id or not self.task_evidence_file:
            raise RuntimeError("voice-004 requires an eligible Pi Durable worker and task-side evidence")
        session = self.request("POST", "/api/pi-sessions/new",
                               headers={"idempotency-key": "voice-004-" + os.urandom(12).hex()}).json()
        session_id = session["id"]
        self.request("POST", "/api/pi-message", json={
            "message": f"Attach this voice conversation to running task {self.task_id} using attach_task_session."
        })
        routing = self.grain_routing()
        if routing.get("task-id") != self.task_id or routing.get("conversation-id") != session_id:
            raise AssertionError("eligible task did not attach to selected Grain conversation")
        before = self.request("GET", f"/api/pi-sessions/{session_id}").json()
        evidence = Path(self.task_evidence_file)
        previous_records = len(evidence.read_text().splitlines()) if evidence.exists() else 0
        result = self.media()[0]
        assert_result(result)
        if not result["transcriptions"]:
            raise AssertionError("task-bound spoken utterance was not recognized")
        after = self.request("GET", f"/api/pi-sessions/{session_id}").json()
        saved_before = {entry.get("id") for entry in before.get("entries", [])}
        new_saved_users = [entry for entry in after.get("entries", [])
                           if entry.get("role") == "user" and entry.get("id") not in saved_before]
        if new_saved_users:
            raise AssertionError("orchestrator saved history claimed task-bound spoken input")
        records = [json.loads(line) for line in evidence.read_text().splitlines()[previous_records:]]
        recognized = [item.get("data", {}).get("text") for item in result["transcriptions"]
                      if isinstance(item.get("data"), dict)]
        hashes = {hashlib.sha256(text.strip().encode()).hexdigest()
                  for text in recognized if isinstance(text, str) and text.strip()}
        if not hashes:
            raise AssertionError("recognized task prompt has no inspectable text")
        if not any(record.get("taskId") == self.task_id and record.get("promptSha256") in hashes
                   for record in records):
            raise AssertionError("task worker did not settle the recognized spoken prompt")

    def voice_005(self) -> None:
        self.assert_v2_binding()
        if not self.stt_outage_file:
            raise RuntimeError("voice-005 requires a private VOICE_STT_OUTAGE_FILE on an isolated broker")
        session = self.request("POST", "/api/pi-sessions/new",
                               headers={"idempotency-key": "voice-005-" + os.urandom(12).hex()}).json()
        session_id = session["id"]
        outage = Path(self.stt_outage_file)
        baseline_user_ids: set[object] = set()

        async def disable_stt() -> None:
            async with httpx.AsyncClient(timeout=10) as client:
                response = await client.get(self.edge_url + f"/api/pi-sessions/{session_id}",
                                            headers=self.headers())
                response.raise_for_status()
                baseline_user_ids.update(entry.get("id") for entry in response.json().get("entries", [])
                                         if entry.get("role") == "user")
            outage.write_text("unavailable\n")

        try:
            result = replace(self, timeout=min(self.timeout, 45)).media(
                before_input=disable_stt)[0]
        finally:
            outage.unlink(missing_ok=True)
        if result["response_completed"] or result["output_bytes"]:
            raise AssertionError(
                "speech outage reported a successful spoken response: "
                f"completed={result['response_completed']}, output_bytes={result['output_bytes']}, "
                f"events={result['events']}, "
                f"messages={[message.get('type') for message in result['server_messages']]}"
            )
        if not any(message.get("type") == "voice-error" for message in result["server_messages"]):
            raise AssertionError("speech outage was not visible to the owner media client")
        during = self.request("GET", f"/api/pi-sessions/{session_id}").json()
        during_user_ids = {entry.get("id") for entry in during.get("entries", [])
                           if entry.get("role") == "user"}
        if during_user_ids != baseline_user_ids:
            raise AssertionError("failed recognition admitted a spoken turn")
        recovered = self.media()[0]
        assert_result(recovered)
        after = self.request("GET", f"/api/pi-sessions/{session_id}").json()
        after_user_ids = {entry.get("id") for entry in after.get("entries", [])
                          if entry.get("role") == "user"}
        if after_user_ids == during_user_ids:
            raise AssertionError("speech capability did not recover")

    def voice_006(self) -> None:
        self.assert_v2_binding()
        with httpx.Client(timeout=30) as client:
            for path in ("/api/pi-transcript", "/api/pi-sessions"):
                response = client.get(self.edge_url + path,
                                      headers={"Origin": self.origin,
                                               "tailscale-user-login": "foreign@example.invalid"})
                if response.status_code != 403:
                    raise AssertionError(f"foreign owner read {path}: HTTP {response.status_code}")
            response = client.post(self.edge_url + "/api/offer",
                                   headers={"Origin": self.origin,
                                            "tailscale-user-login": "foreign@example.invalid"},
                                   json={"sdp": "invalid", "type": "offer"})
            if response.status_code != 403:
                raise AssertionError(f"foreign owner signaling: HTTP {response.status_code}")


