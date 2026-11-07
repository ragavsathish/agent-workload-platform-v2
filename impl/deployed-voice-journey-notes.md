# Slice 05 implementation and qualification notes

Cites the [slice plan](../docs/plan/05-deployed-voice-journey/plan.md), [pinned voice probe](../raw/v1/smallwebrtc_voice.py), [testing guide](../raw/v1/voice-testing.md), [host requirements](../raw/v1/host-requirements.md), [environment example](../raw/v1/voice-env.example), and [slice 04 implementation note](live-session-integration-notes.md).

## Current baseline

At `0328fbb`, the local gate exercises Grain decisions, a production Pi HTTP route and session manager, broker forwarding, task socket protocol, and Python edge import. It does not create a WebRTC peer, send audio through Pipecat, run MLX recognition or synthesis, boot the persistent Gondolin VM, or run the deployed task worker. No deployed qualification is asserted by this planning turn.

## Build topology

The retained edge runs `SmallWebRTCTransport` in `products/voice-agent/apps/edge/bot.py`. It uses Pipecat OpenAI-compatible STT and TTS adapters against the bearer-protected native broker, and an OpenAI-compatible dialogue adapter against the broker `/pi` path. The broker forwards to the Pi voice service; the Pi service binds selected conversation and turn decisions to the Grain bridge. Preserve `effect@3.22.4` in the TypeScript bridge and attachment path. The pinned Python runtime uses asyncio and httpx; a Python functional library is not a prerequisite for this slice because no new functional rewrite is specified.

Build a protocol driver from the [pinned SmallWebRTC probe](../raw/v1/smallwebrtc_voice.py). Its `aiortc` peer and timed 20 ms PCM frames are a valid physical microphone and browser substitute. The driver must enter via the production signaling route and assert received audio, recognition, Pi/Grain turn identity, and safe public transcript. It must not call STT, Pi, or Grain directly to claim `voice-001`. The existing v1 probe assumes Compose service names, broker token files, and v1 paths; adapt these in the implementation turn rather than copying its deployment assumptions blindly.

## Locally controllable and deployed prerequisites

Local work can supply a protocol peer, synthetic microphone audio, fixed owner identity, ephemeral service secrets, deterministic model protocol outputs, and fresh Grain/Pi data. Such a run qualifies the route, identity, timing, and persistence it actually exercises. A fake transcription or speech server must be labeled a provider substitution and cannot qualify native MLX speech.

Deployed media qualification needs an Apple Silicon host, the locked Python/Node environments, live Parakeet and Kokoro model assets, a working Pi model credential/provider, owner ingress and exact owner mapping, the native broker and Pi voice service, and a reachable public edge. The pinned [host requirements](../raw/v1/host-requirements.md) and [environment example](../raw/v1/voice-env.example) are v1 setup evidence, not a v2 deployment script. V2 currently has no complete host service installer, public ingress deployment, or model provisioning path. Persistent voice VM qualification additionally requires built Gondolin assets and its configured image path, permitted workspace, and successful actual VM boot. Durable task qualification separately requires a deployed owner/worker and an eligible task with a real socket and result. Do not infer either from the local task socket driver in slice 04.

## Verification order and record

1. Run the local docs, hash, and behavioral checks; record the initial failing named media acceptance cases before code changes.
2. Bring up fresh Grain, Pi, broker, and edge processes with explicit loopback boundaries. Run `voice-001` through `voice-006` with a live protocol peer. Capture signaling status, RTP input/output, recognized text, one turn identity and destination, and a post-restart public read.
3. On the deployed host, inspect service and model readiness, then run a fresh public voice journey with native recognition and synthesis. Capture the provider versions, owner-safe correlation IDs, response audio evidence, transcript, and result. Run actual VM boot and task worker journeys as separate assertions when those assets exist.

The evidence report must name the precise entry and provider substitutions. A service configuration check, Python import, direct HTTP chat test, or healthy model endpoint alone cannot mark the deployed voice journey complete. Avoid recording tokens, raw private audio, or full private tool output in committed evidence.
