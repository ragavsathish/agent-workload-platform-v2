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

for id in $(rg -o '@lifecycle-[0-9]+' docs/plan/02-task-lifecycle/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/lifecycle_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named test" >&2
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

for id in $(rg -o '@session-[0-9]+' docs/plan/03-session-orchestration/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/session_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named test" >&2
    exit 1
  fi
done

for id in $(rg -o '@knowledge-[0-9]+' docs/plan/07-personal-knowledge/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/knowledge_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named test" >&2
    exit 1
  fi
done

for id in $(rg -o '@(extraction|projection|migration|qualification)-[0-9]+' docs/plan/08-knowledge-extraction-migration/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/knowledge_migration_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named test" >&2
    exit 1
  fi
done

for id in $(rg -o '@song-[0-9]+' docs/plan/09-personal-song/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/song_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named test" >&2
    exit 1
  fi
done

for id in $(rg -o '@taxonomy-[0-9]+' docs/plan/10-knowledge-taxonomy/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/knowledge_taxonomy_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named test" >&2
    exit 1
  fi
done

for id in $(rg -o '@incident-[0-9]+' docs/plan/11-operational-incidents/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/incident_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named test" >&2
    exit 1
  fi
done

for id in $(rg -o '@artifact-[0-9]+' docs/plan/12-task-result-delivery/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/artifact_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named test" >&2
    exit 1
  fi
done

echo "documentation gate OK"
