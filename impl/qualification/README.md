# Slice 05 voice qualification

Cites the [slice 05 scenarios](../../docs/plan/05-deployed-voice-journey/spec.feature), [implementation notes](../deployed-voice-journey-notes.md), and [pinned media probe](../../raw/v1/smallwebrtc_voice.py).

The named `voice-001` through `voice-006` tests were recorded red on 2026-11-07 before the media driver existed: all six raised `ModuleNotFoundError: No module named 'voice_media'`. They are an opt-in deployed acceptance gate. `harness/verify.sh` checks their syntax but does not represent a deployed media pass.

Run `harness/qualify-voice.sh` only against an explicitly configured v2 stack. Supply `VOICE_EDGE_URL`, `VOICE_GRAIN_URL`, `VOICE_GRAIN_TOKEN`, `VOICE_ORIGIN`, `VOICE_OWNER_IDENTITY`, and `VOICE_UTTERANCE`. For input speech, supply a mono PCM16 16 kHz `VOICE_AUDIO_WAV`, or `VOICE_BROKER_URL` and `VOICE_BROKER_TOKEN` to synthesize the utterance using the real broker. `VOICE_LOCAL_INGRESS=1` applies only to an isolated loopback ingress with its trusted marker path. The driver creates a real `aiortc` offer, sends 20 ms RTP audio, receives response media, reads the owner transcript, and compares the public selection to v2 Grain. The [stack template](voice-stack.env.example) identifies service variables without storing credentials.

The gate currently has executable public media, restart, and foreign-owner checks for `voice-001`, `voice-002`, and `voice-006`. `voice-002` requires `VOICE_RESTART_ARGV_JSON`, a JSON array of the exact service restart command. The remaining named tests fail with explicit prerequisites for stream interruption with recorded turn inspection, a deployed eligible task with task-side evidence, and isolated speech provider outage. Those are unresolved build items, not passing tests. A full slice 05 qualification remains open until the named tests run green against the deployed services and the evidence is retained.

The current host has v1 native broker and Pi Voice Service listeners on `127.0.0.1:9443` and `:9446`. They are not a v2 Grain media deployment and were not modified by this build. To avoid them, `run_local_stack.py` starts isolated v2 Grain, Pi, broker, and edge processes on `9547`, `9546`, `9543`, and `7861`, with fresh state and secrets. It requires a private host dotenv with working model, Pi, and Gondolin settings. For example:

```sh
impl/runtime/products/voice-agent/apps/edge/.venv/bin/python \
  impl/qualification/run_local_stack.py \
  --host-env "$HOME/Library/Application Support/PipecatEdge/apps/pi-voice/pi-voice.env" \
  --state-dir /tmp/awp-v2-voice-qualification
```

The launcher checks the broker-to-Pi route before running the named gate. Its `--test` option runs one named unittest. The private state directory holds service logs and `pi-process.json`, including process environment secrets; keep it outside the repository and restrict access. `AWP_WEBRTC_LOOPBACK_ONLY=1` scopes same-host ICE candidates to loopback for this launcher only. A real public ingress needs its own ICE reachability and authentication setup.

On this host, a fresh isolated local run passed `voice-001` with real WebRTC RTP input, native Parakeet recognition, a v2 Grain-selected Pi turn, and Kokoro response media; `voice-002` passed the same audio journey followed by a fresh Pi service restart and public selected-history read; `voice-006` rejected foreign-owner reads and signaling. These are local owner-ingress checks. `voice-001` currently checks an accepted recognized turn and response media but does not prove exactly-once dispatch by a separate turn ledger count or compare the spoken waveform with the transcript answer. `voice-002` checks identical persisted history after restart, but lacks a separate dispatch counter. The Gondolin voice VM booted as part of Pi readiness; its image identity and long-running durability were not qualified. No public tailnet media ingress or deployed task worker was exercised.
