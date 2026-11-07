#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

"$ROOT/harness/check-docs.sh"
shasum -a 256 -c raw/v1/SHA256SUMS
allium check specs
"$ROOT/impl/test.sh"
(cd "$ROOT/impl" && clojure -M:verify)
"$ROOT/harness/probe.sh"
echo "verify OK: documentation, source hashes, behavior, runtime topology, and public module probe"
