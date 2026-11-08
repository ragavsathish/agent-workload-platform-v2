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

for id in $(rg -o '@quality-[0-9]+' docs/plan/17-knowledge-quality/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/knowledge_quality_acceptance_test.clj \
      impl/runtime/products/voice-agent/apps/pi-agent/knowledge-quality-v2.test.mjs; then
    echo "Acceptance scenario $id has no named check" >&2
    exit 1
  fi
done

for id in $(rg -o '@continuity-[0-9]+' docs/plan/18-private-knowledge-continuity/spec.feature | tr -d '@'); do
  test_id="${id//-/_}"
  if ! rg -q "$test_id" impl/knowledge-migration/test_continuity.py; then
    echo "Acceptance scenario $id has no named check" >&2
    exit 1
  fi
done

for id in $(rg -o '@workbench-[0-9]+' docs/plan/21-private-review-workbench/spec.feature | tr -d '@'); do
  test_id="${id//-/_}"
  if ! rg -q "$test_id" impl/private-review/test_workbench.py; then
    echo "Acceptance scenario $id has no named check" >&2
    exit 1
  fi
done

for id in $(rg -o '@candidate-[0-9]+' docs/plan/22-public-v2-candidate/spec.feature | tr -d '@'); do
  test_id="${id//-/_}"
  if ! rg -q "$test_id" impl/deployment/test_public_candidate.py; then
    echo "Acceptance scenario $id has no named check" >&2
    exit 1
  fi
done

for id in $(rg -o '@relation-[0-9]+' docs/plan/19-grounded-relations/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/grounded_relations_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named check" >&2
    exit 1
  fi
done

for id in $(rg -o '@reliability-[0-9]+' docs/plan/20-relation-model-reliability/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/relation_model_reliability_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named check" >&2
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

for id in $(rg -o '@delivery-[0-9]+' docs/plan/13-task-worker-delivery/spec.feature | tr -d '@'); do
  test_id="${id//-/_}"
  if ! rg -q "$id|$test_id" impl/deployment/test_task_worker_delivery.py impl/deployment/test_artifact_delivery.py impl/test/awp_v2/artifact_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named test" >&2
    exit 1
  fi
done

for id in $(rg -o '@release-[0-9]+' docs/plan/15-local-release-readiness/spec.feature | tr -d '@'); do
  test_id="${id//-/_}"
  if ! rg -q "$test_id" impl/qualification/test_local_release.py; then
    echo "Acceptance scenario $id has no named test" >&2
    exit 1
  fi
done

for id in $(rg -o '@durable-song-[0-9]+' docs/plan/16-durable-song-owner/spec.feature | tr -d '@'); do
  if ! rg -q "$id" impl/test/awp_v2/song_acceptance_test.clj \
      impl/runtime/platform/agent-execution/pi-durable-task/song-lyric-owner.test.mjs \
      impl/qualification/qualify_durable_song.py; then
    echo "Acceptance scenario $id has no named check" >&2
    exit 1
  fi
done

for id in $(rg -o '@deployment-[0-9]+' docs/plan/23-deployment-levels/spec.feature | tr -d '@'); do
  test_id="${id//-/_}"
  if ! rg -q "$id|$test_id" impl/test/awp_v2/deployment_acceptance_test.clj impl/deployment/test_deployment_levels.py; then
    echo "Acceptance scenario $id has no named check" >&2
    exit 1
  fi
done

for id in $(rg -o '@owner-client-[0-9]+' docs/plan/24-owner-client/spec.feature | tr -d '@'); do
  test_id="${id//-/_}"
  if ! rg -q "$id|$test_id" \
      impl/runtime/products/voice-agent/apps/client/test/owner_workspace_acceptance_test.dart \
      impl/runtime/products/voice-agent/apps/client/test/owner_task_routing_acceptance_test.dart \
      impl/runtime/products/voice-agent/apps/client/test/transcript_view_test.dart \
      impl/runtime/products/voice-agent/apps/client/test/embedded_navigation_policy_test.dart \
      impl/runtime/products/voice-agent/apps/client/browser_test/mcp_app_renderers.browser.test.js \
      impl/runtime/products/voice-agent/apps/client/browser_test/sandbox.security.test.mjs \
      impl/test/awp_v2/artifact_acceptance_test.clj \
      impl/test/awp_v2/session_acceptance_test.clj; then
    echo "Acceptance scenario $id has no named client or server check" >&2
    exit 1
  fi
done

for id in $(rg -o '@routing-[0-9]+' docs/plan/25-task-routing-integration/spec.feature | tr -d '@'); do
  test_id="${id//-/_}"
  if ! rg -q "$id|$test_id" \
      impl/test/awp_v2/session_acceptance_test.clj \
      impl/runtime/products/voice-agent/apps/pi-agent/live-protocol.test.mjs \
      impl/runtime/products/voice-agent/apps/client/test/owner_task_routing_acceptance_test.dart \
      impl/runtime/products/voice-agent/apps/broker/test_live_proxy.py \
      impl/runtime/products/voice-agent/apps/edge/test_task_routing.py; then
    echo "Acceptance scenario $id has no named routing check" >&2
    exit 1
  fi
done

echo "documentation gate OK"
