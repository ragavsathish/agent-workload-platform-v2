#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

for file in docs/wiki/*.md docs/plan/*/*.md; do
  if rg -ni '\b(Grain|Clojure|LMDB|SQLite|Postgres|Python|TypeScript|Allium|React|Docker|Kubernetes)\b' "$file"; then
    echo "Spec firewall failed: implementation vocabulary in $file" >&2
    exit 1
  fi
done

for file in docs/wiki/*.md docs/plan/*/*.md docs/wow/*.md; do
  if ! rg -q '\]\([^)]*\)|Cites ' "$file"; then
    echo "Reference rule failed: $file has no citation" >&2
    exit 1
  fi
done

for id in $(rg -o '@admission-[0-9]+' docs/plan/01-task-admission/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/tasking_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named test" >&2
    exit 1
  fi
done

echo "documentation gate OK"
