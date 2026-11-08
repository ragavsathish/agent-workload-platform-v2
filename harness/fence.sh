#!/usr/bin/env bash
set -euo pipefail
TARGET="${1:?usage: harness/fence.sh /path/to/new-room}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[[ ! -e "$TARGET" ]] || { echo "FENCE FAILED: destination exists" >&2; exit 1; }
[[ -z "$(git -C "$ROOT" status --porcelain)" ]] || {
  echo "FENCE FAILED: commit the documented build before fencing" >&2
  exit 1
}
"$ROOT/harness/verify.sh"
git clone -q --depth 1 "file://$ROOT" "$TARGET"
[[ -d "$TARGET/impl" ]] || { echo "FENCE FAILED: no implementation to remove" >&2; exit 1; }
git -C "$TARGET" rm -qr impl
git -C "$TARGET" -c user.name=fence -c user.email=fence@localhost \
  commit -q -m "Remove prior implementation for regeneration"
TREE="$(git -C "$TARGET" rev-parse 'HEAD^{tree}')"
NEW_ROOT="$(git -C "$TARGET" -c user.name=fence -c user.email=fence@localhost \
  commit-tree "$TREE" -m 'Fenced clean room')"
git -C "$TARGET" reset -q --hard "$NEW_ROOT"
git -C "$TARGET" remote remove origin
git -C "$TARGET" reflog expire --expire=now --all
git -C "$TARGET" gc -q --prune=now --aggressive
[[ ! -e "$TARGET/impl" ]] || { echo "FENCE FAILED: implementation remains" >&2; exit 1; }
[[ -f "$TARGET/docs/plan/08-knowledge-extraction-migration/spec.feature" ]] || {
  echo "FENCE FAILED: slice 08 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/v1/knowledge-processor.mjs" ]] || {
  echo "FENCE FAILED: pinned extraction evidence missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/09-personal-song/spec.feature" ]] || {
  echo "FENCE FAILED: slice 09 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/v1/song-service.py" ]] || {
  echo "FENCE FAILED: pinned song evidence missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/10-knowledge-taxonomy/spec.feature" ]] || {
  echo "FENCE FAILED: slice 10 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/orc/manifest.md" ]] || {
  echo "FENCE FAILED: pinned ORC evidence missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/11-operational-incidents/spec.feature" ]] || {
  echo "FENCE FAILED: slice 11 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/v1/stack-alert-monitor.py" ]] || {
  echo "FENCE FAILED: pinned incident evidence missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/12-task-result-delivery/spec.feature" ]] || {
  echo "FENCE FAILED: slice 12 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/v1/task-artifacts.py" ]] || {
  echo "FENCE FAILED: pinned task artifact evidence missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/13-task-worker-delivery/spec.feature" ]] || {
  echo "FENCE FAILED: slice 13 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/v1/task-image-ensure.py" ]] || {
  echo "FENCE FAILED: pinned task image evidence missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/15-local-release-readiness/spec.feature" ]] || {
  echo "FENCE FAILED: slice 15 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/specs/local-release-readiness.allium" ]] || {
  echo "FENCE FAILED: slice 15 behavior missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/16-durable-song-owner/spec.feature" ]] || {
  echo "FENCE FAILED: slice 16 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/v1/song-owner-server.ts" ]] || {
  echo "FENCE FAILED: pinned lyric owner protocol missing" >&2; exit 1;
}
[[ -f "$TARGET/specs/durable-song-owner.allium" ]] || {
  echo "FENCE FAILED: slice 16 behavior missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/17-knowledge-quality/spec.feature" ]] || {
  echo "FENCE FAILED: slice 17 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/v1/journal-reviewed-manifest.schema.json" ]] || {
  echo "FENCE FAILED: pinned quality evidence missing" >&2; exit 1;
}
[[ -f "$TARGET/specs/knowledge-quality.allium" ]] || {
  echo "FENCE FAILED: slice 17 behavior missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/18-private-knowledge-continuity/spec.feature" ]] || {
  echo "FENCE FAILED: slice 18 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/specs/private-knowledge-continuity.allium" ]] || {
  echo "FENCE FAILED: slice 18 behavior missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/19-grounded-relations/spec.feature" ]] || {
  echo "FENCE FAILED: slice 19 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/orc/ontology-exploration.clj" ]] || {
  echo "FENCE FAILED: pinned ORC relation discovery missing" >&2; exit 1;
}
[[ -f "$TARGET/specs/grounded-relations.allium" ]] || {
  echo "FENCE FAILED: slice 19 behavior missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/20-relation-model-reliability/spec.feature" ]] || {
  echo "FENCE FAILED: slice 20 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/relation-stage-observations.md" ]] || {
  echo "FENCE FAILED: pinned relation staging evidence missing" >&2; exit 1;
}
[[ -f "$TARGET/specs/relation-model-reliability.allium" ]] || {
  echo "FENCE FAILED: slice 20 behavior missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/21-private-review-workbench/spec.feature" ]] || {
  echo "FENCE FAILED: slice 21 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/v1/journal-review-mcp-server.ts" ]] || {
  echo "FENCE FAILED: pinned private review evidence missing" >&2; exit 1;
}
[[ -f "$TARGET/specs/private-review-workbench.allium" ]] || {
  echo "FENCE FAILED: slice 21 behavior missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/22-public-v2-candidate/spec.feature" ]] || {
  echo "FENCE FAILED: slice 22 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/v1/public-candidate-host-observation.md" ]] || {
  echo "FENCE FAILED: pinned public candidate host observation missing" >&2; exit 1;
}
[[ -f "$TARGET/specs/public-v2-candidate.allium" ]] || {
  echo "FENCE FAILED: slice 22 behavior missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/23-deployment-levels/spec.feature" ]] || {
  echo "FENCE FAILED: slice 23 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/specs/deployment-levels.allium" ]] || {
  echo "FENCE FAILED: slice 23 behavior missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/24-owner-client/spec.feature" ]] || {
  echo "FENCE FAILED: slice 24 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/wiki/owner-client.md" ]] || {
  echo "FENCE FAILED: slice 24 domain rules missing" >&2; exit 1;
}
[[ -f "$TARGET/specs/owner-client.allium" ]] || {
  echo "FENCE FAILED: slice 24 behavior missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/plan/25-task-routing-integration/spec.feature" ]] || {
  echo "FENCE FAILED: slice 25 contract missing" >&2; exit 1;
}
[[ -f "$TARGET/docs/wiki/task-routing.md" ]] || {
  echo "FENCE FAILED: slice 25 domain rules missing" >&2; exit 1;
}
[[ -f "$TARGET/raw/v1/client-source-capture.md" && -d "$TARGET/raw/v1/client" ]] || {
  echo "FENCE FAILED: pinned owner client evidence missing" >&2; exit 1;
}
[[ "$(git -C "$TARGET" rev-list --all --count)" == 1 ]] || {
  echo "FENCE FAILED: prior history remains" >&2; exit 1
}
if git -C "$TARGET" rev-list --objects --all | rg ' impl/'; then
  echo "FENCE FAILED: implementation object remains" >&2; exit 1
fi
echo "fence OK: single-commit room with prior implementation removed"
