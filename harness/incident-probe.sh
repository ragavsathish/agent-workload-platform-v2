#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIR="$(mktemp -d)"
trap 'rm -rf "$DIR"' EXIT
cd "$ROOT/impl"
for phase in create verify; do
  clojure -J--add-opens=java.base/java.nio=ALL-UNNAMED \
    -J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
    -M -m awp-v2.incident.probe "$phase" "$DIR"
done
