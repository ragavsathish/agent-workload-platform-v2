#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

"$ROOT/harness/check-docs.sh"
shasum -a 256 -c raw/v1/SHA256SUMS
shasum -a 256 -c raw/orc/SHA256SUMS
allium check specs
"$ROOT/impl/test.sh"
(cd "$ROOT/impl" && clojure -M:verify)
CLIENT="$ROOT/impl/runtime/products/voice-agent/apps/client"
pnpm --dir "$ROOT/impl/runtime" install --frozen-lockfile --ignore-scripts
pnpm --dir "$ROOT/impl/runtime" --filter @agent-workload-platform/voice-agent-client run build:bridge
pnpm --dir "$ROOT/impl/runtime" --filter @agent-workload-platform/voice-agent-client run test:bridge-security
(cd "$CLIENT" && ./tool/flutterw pub get --enforce-lockfile)
(cd "$CLIENT" && ./tool/flutterw analyze)
(cd "$CLIENT" && ./tool/flutterw test)
(cd "$CLIENT" && ./tool/flutterw build web --wasm --release)
pnpm --dir "$ROOT/impl/runtime" run build
node --test "$ROOT/impl/runtime/platform/agent-execution/pi-durable-task/song-lyric-owner.test.mjs"
pnpm --dir "$ROOT/impl/runtime/products/voice-agent/apps/pi-agent" exec node --test live-protocol.test.mjs knowledge-worker.test.mjs knowledge-quality.test.mjs knowledge-quality-v2.test.mjs song-worker.test.mjs
node --check "$ROOT/impl/runtime/products/voice-agent/apps/pi-agent/knowledge-quality-run.mjs"
(cd "$ROOT/impl/runtime/products/voice-agent/apps/broker" &&
  uv sync --frozen --no-dev --quiet &&
  PYTHONPATH=src:../../packages/telemetry/src .venv/bin/python test_live_proxy.py)
(cd "$ROOT/impl/runtime/products/voice-agent/apps/edge" &&
  uv sync --frozen --no-dev --quiet &&
  PYTHONPATH=.:../../packages/telemetry/src .venv/bin/python -c 'import bot' &&
  PYTHONPATH=.:../../packages/telemetry/src .venv/bin/python -m unittest test_task_routing.py -v)
python3 - "$ROOT/impl/runtime" <<'PY'
import ast
import pathlib
import sys

runtime = pathlib.Path(sys.argv[1])
sources = [
    *runtime.joinpath("products/voice-agent/apps/edge").glob("*.py"),
    *runtime.joinpath("products/voice-agent/apps/broker/src").rglob("*.py"),
    *runtime.joinpath("products/voice-agent/packages/telemetry/src").rglob("*.py"),
]
for source in sources:
    ast.parse(source.read_text(encoding="utf-8"), filename=str(source))
PY
python3 -m py_compile \
  "$ROOT/impl/qualification/voice_media.py" \
  "$ROOT/impl/qualification/test_voice_media.py" \
  "$ROOT/impl/qualification/run_local_stack.py" \
  "$ROOT/impl/qualification/restart_local_pi.py" \
  "$ROOT/impl/qualification/sitecustomize.py"
python3 -m py_compile "$ROOT/impl/qualification/local_release.py" \
  "$ROOT/impl/qualification/qualify_durable_song.py" \
  "$ROOT/impl/qualification/restart_supervised_pi.py" \
  "$ROOT/impl/qualification/incident_source.py" \
  "$ROOT/impl/qualification/test_local_release.py"
python3 -m unittest discover -s "$ROOT/impl/qualification" -p test_local_release_model.py -v
(cd "$ROOT/impl/qualification" && "$ROOT/impl/runtime/products/voice-agent/apps/edge/.venv/bin/python" -m unittest test_voice_media_unit.py)
python3 -m py_compile "$ROOT/impl/deployment/deployment.py" \
  "$ROOT/impl/deployment/gateway.py" "$ROOT/impl/deployment/stack.py" \
  "$ROOT/impl/deployment/public_candidate.py" \
  "$ROOT/impl/deployment/qualify_public.py" \
  "$ROOT/impl/deployment/confirm_public_report.py"
python3 -m unittest discover -s "$ROOT/impl/deployment" -p test_deployment.py -v
python3 -m unittest discover -s "$ROOT/impl/deployment" -p test_deployment_levels.py -v
python3 -m unittest discover -s "$ROOT/impl/deployment" -p test_public_candidate.py -v
python3 -m unittest discover -s "$ROOT/impl/deployment" -p test_artifact_delivery.py -v
python3 -m unittest discover -s "$ROOT/impl/deployment" -p test_task_worker_delivery.py -v
python3 -m unittest discover -s "$ROOT/impl/knowledge-migration" -p test_snapshot.py -v
python3 -m unittest discover -s "$ROOT/impl/knowledge-migration" -p test_continuity.py -v
python3 -m py_compile "$ROOT/impl/private-review/workbench.py" "$ROOT/impl/private-review/server.py"
python3 -m unittest discover -s "$ROOT/impl/private-review" -p test_workbench.py -v
if command -v docker >/dev/null 2>&1; then
  AWP_PUBLIC_PROJECT=awp-v2-verify AWP_PUBLIC_NODE=awp-v2-verify-node \
  AWP_BROKER_PORT=9743 AWP_GITEA_PORT=9750 AWP_PUBLIC_EDGE_ENV="$ROOT/impl/deployment/edge.env.example" \
    docker compose -f "$ROOT/impl/deployment/compose.yml" config --quiet
fi
"$ROOT/harness/probe.sh"
"$ROOT/harness/session-probe.sh"
"$ROOT/harness/knowledge-probe.sh"
"$ROOT/harness/song-probe.sh"
"$ROOT/harness/incident-probe.sh"
echo "verify OK: documentation, source hashes, behavior, runtime topology, and public module probe"
