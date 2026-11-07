#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PYTHON="$ROOT/impl/runtime/products/voice-agent/apps/edge/.venv/bin/python"
[[ -x "$PYTHON" ]] || { echo "voice qualification requires the locked edge environment; run uv sync --frozen in impl/runtime/products/voice-agent/apps/edge" >&2; exit 2; }
if [[ -z "${VOICE_EDGE_URL:-}" || -z "${VOICE_GRAIN_URL:-}" || -z "${VOICE_GRAIN_TOKEN:-}" || -z "${VOICE_ORIGIN:-}" || -z "${VOICE_OWNER_IDENTITY:-}" || -z "${VOICE_UTTERANCE:-}" ]]; then
  echo "voice qualification requires VOICE_EDGE_URL, VOICE_GRAIN_URL, VOICE_GRAIN_TOKEN, VOICE_ORIGIN, VOICE_OWNER_IDENTITY, and VOICE_UTTERANCE" >&2
  exit 2
fi
if [[ -z "${VOICE_AUDIO_WAV:-}" && ( -z "${VOICE_BROKER_URL:-}" || -z "${VOICE_BROKER_TOKEN:-}" ) ]]; then
  echo "set VOICE_AUDIO_WAV or both VOICE_BROKER_URL and VOICE_BROKER_TOKEN" >&2
  exit 2
fi
"$PYTHON" -m unittest discover -s "$ROOT/impl/qualification" -p test_voice_media.py -v
