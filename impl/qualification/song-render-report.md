# Slice 09 real render qualification

Cites the [slice 09 plan](../../docs/plan/09-personal-song/plan.md) and [Effect render worker](../runtime/products/voice-agent/apps/pi-agent/song-worker.ts).

On 2026-11-08, the host ACE-Step health endpoint reported `models_initialized=true`, `llm_initialized=true`, `acestep-v15-turbo`, and `acestep-5Hz-lm-1.7B`. A synthetic, non-private 30-second manifest was passed through the new Effect worker, the ACE-Step task protocol, audio download, FFmpeg encoding, and private local artifact store. The worker returned artifact ID `b8ee0a18-3383-44ae-a579-395e19a61afe`.

The artifact is outside the repository at `/tmp/awp-song-qualification/b8ee0a18-3383-44ae-a579-395e19a61afe.m4a`. SHA-256: `882242cd998957fc4f135e5c6ac99f134ce27e2db360e7b04e6f8898efa1c0c4`. Media inspection found AAC, 48 kHz stereo, 30.000 seconds, 945,151 bytes, mean volume -16.6 dB, and peak volume -1.3 dB. This proves a real encoded, non-silent render on this host. No subjective listening or lyric intelligibility judgment was made. The deployed owner route and tailnet media path are separate open qualification gates.
