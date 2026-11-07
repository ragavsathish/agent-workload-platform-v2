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
[[ "$(git -C "$TARGET" rev-list --all --count)" == 1 ]] || {
  echo "FENCE FAILED: prior history remains" >&2; exit 1
}
if git -C "$TARGET" rev-list --objects --all | rg ' impl/'; then
  echo "FENCE FAILED: implementation object remains" >&2; exit 1
fi
echo "fence OK: single-commit room with prior implementation removed"
