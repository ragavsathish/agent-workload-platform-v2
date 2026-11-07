# Slice 05 voice qualification

Cites the [slice 05 scenarios](../../docs/plan/05-deployed-voice-journey/spec.feature), [implementation notes](../deployed-voice-journey-notes.md), and [pinned media probe](../../raw/v1/smallwebrtc_voice.py).

The named `voice-001` through `voice-006` tests were recorded red on 2026-11-07 before the media driver existed: all six raised `ModuleNotFoundError: No module named 'voice_media'`. They are an opt-in deployed acceptance gate. `harness/verify.sh` checks their syntax but does not represent a deployed media pass.

Run `harness/qualify-voice.sh` only against an explicitly configured v2 stack. Supply `VOICE_EDGE_URL`, `VOICE_GRAIN_URL`, `VOICE_GRAIN_TOKEN`, `VOICE_ORIGIN`, `VOICE_OWNER_IDENTITY`, and `VOICE_UTTERANCE`. For input speech, supply a mono PCM16 16 kHz `VOICE_AUDIO_WAV`, or `VOICE_BROKER_URL` and `VOICE_BROKER_TOKEN` to synthesize the utterance using the real broker. `VOICE_LOCAL_INGRESS=1` applies only to an isolated loopback ingress with its trusted marker path. The driver creates a real `aiortc` offer, sends 20 ms RTP audio, receives response media, reads the owner transcript, and compares the public selection to v2 Grain. The [stack template](voice-stack.env.example) identifies service variables without storing credentials.

All six named tests are executable against a properly configured v2 stack. `voice-002` requires `VOICE_RESTART_ARGV_JSON`, a JSON array of the controlled Pi service restart command. `voice-003` uses an isolated, file-gated turn hold to make disconnect timing deterministic and reads the Grain stop request and outcome. `voice-004` requires an eligible Grain task, a running Pi Durable worker socket, and task-side SHA-256 evidence. `voice-005` uses an isolated, file-gated STT outage, checks the owner's `voice-error` signal, and retries after recovery. `voice-001` checks one new accepted Grain turn, one broker forwarding record for its ID, and non-silent response audio. A full deployed slice 05 qualification remains open until all named tests run green through protected public ingress.

The current host has v1 native broker and Pi Voice Service listeners on `127.0.0.1:9443` and `:9446`. They are not a v2 Grain media deployment and were not modified by this build. To avoid them, `run_local_stack.py` starts isolated v2 Grain, Pi, broker, and edge processes on `9547`, `9546`, `9543`, and `7861`, with fresh state and secrets. It requires a private host dotenv with working model, Pi, and Gondolin settings. For example:

```sh
impl/runtime/products/voice-agent/apps/edge/.venv/bin/python \
  impl/qualification/run_local_stack.py \
  --host-env "$HOME/Library/Application Support/PipecatEdge/apps/pi-voice/pi-voice.env" \
  --state-dir /tmp/awp-v2-voice-qualification
```

The launcher checks the broker-to-Pi route before running the named gate. For the full gate or `voice-004`, it also seeds an eligible Grain task and starts a real Gondolin-backed Pi Durable worker from v2. Its `--test` option runs one named unittest. The private state directory holds service logs, broker/task evidence, and `pi-process.json`, including process environment secrets; keep it outside the repository and restrict access. `AWP_WEBRTC_LOOPBACK_ONLY=1` scopes same-host ICE candidates to loopback for this launcher only. A real public ingress needs its own ICE reachability and authentication setup.

Focused isolated local runs on this host passed all six scenarios, followed by one complete six-scenario run (`Ran 6 tests in 212.673s`, `OK`) with real WebRTC media, Parakeet STT, Kokoro TTS, Pi/Grain session decisions, and an actual Pi Durable task worker for `voice-004`. The probe counts non-silent PCM output, because WebRTC sends continuous silent RTP while idle. The broker's private dispatch ledger proves one forward to Pi for the recognized `voice-001` turn and no redispatch after `voice-002` restart or `voice-003` reconnect. The task worker records hashes of settled prompts and answers without writing their private text into committed evidence. These are same-host owner-ingress checks; a public tailnet journey, speech-to-answer waveform equivalence, long-running VM durability, and deployment supervision remain unqualified. This host has neither `tailscale` CLI nor Tailscale.app and has no configured v2 public ingress, so a public ICE/auth run cannot be made here.
