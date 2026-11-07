#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOM="$(mktemp -d)"
trap 'rm -rf "$ROOM"' EXIT
cd "$ROOT/impl"
for phase in create verify; do
  clojure -J--add-opens=java.base/java.nio=ALL-UNNAMED \
    -J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
    -M -m awp-v2.knowledge.probe "$phase" "$ROOM"
done
