#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SUPPORT="${HOME}/Library/Application Support/AWPv2/k3d"
CLUSTER="awp-v2-ops"
CONTEXT="k3d-${CLUSTER}"
NAMESPACE="awp-v2"
IMAGE="awp-v2-grain:slice43"
TOKEN_DIR="${SUPPORT}/grain-candidate"
TOKEN_FILE="${TOKEN_DIR}/session-token"
MANIFEST="${ROOT}/impl/deployment/k3d/grain.yaml"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"; }
cluster_ready() {
  k3d cluster list -o json | python3 -c 'import json,sys; d=json.load(sys.stdin); raise SystemExit(0 if any(c.get("name")==sys.argv[1] and c.get("serversRunning",0)>0 for c in d) else 1)' "$CLUSTER"
}

preflight() {
  need docker; need k3d; need kubectl; need curl; need openssl
  docker info >/dev/null 2>&1 || die "Docker engine is not ready"
  cluster_ready || die "k3d cluster ${CLUSTER} is not ready"
  kubectl --context "$CONTEXT" get --raw=/readyz >/dev/null || die "k3d API is not ready"
  [[ -f "$MANIFEST" ]] || die "missing Grain manifest: $MANIFEST"
}

prepare_token() {
  mkdir -p "$TOKEN_DIR"
  chmod 700 "$TOKEN_DIR"
  if [[ ! -f "$TOKEN_FILE" ]]; then
    umask 077
    openssl rand -hex 32 >"$TOKEN_FILE"
  fi
  chmod 600 "$TOKEN_FILE"
}

apply_secret() {
  kubectl --context "$CONTEXT" -n "$NAMESPACE" create secret generic grain-session \
    --from-file=token="$TOKEN_FILE" --dry-run=client -o yaml | \
    kubectl --context "$CONTEXT" apply -f - >/dev/null
}

build_image() {
  docker build --platform linux/arm64 \
    --file "${ROOT}/impl/deployment/k3d/grain.Containerfile" \
    --tag "$IMAGE" "$ROOT"
  k3d image import --cluster "$CLUSTER" "$IMAGE"
}

up() {
  preflight
  prepare_token
  build_image
  kubectl --context "$CONTEXT" apply -f "$MANIFEST"
  apply_secret
  kubectl --context "$CONTEXT" -n "$NAMESPACE" rollout status statefulset/grain --timeout=240s
}

rollout() {
  kubectl --context "$CONTEXT" -n "$NAMESPACE" get pvc grain-data-stable \
    -o jsonpath='pvc_uid_before={.metadata.uid} pv={.spec.volumeName}{"\n"}'
  kubectl --context "$CONTEXT" -n "$NAMESPACE" rollout restart statefulset/grain
  kubectl --context "$CONTEXT" -n "$NAMESPACE" rollout status statefulset/grain --timeout=240s
  kubectl --context "$CONTEXT" -n "$NAMESPACE" get pvc grain-data-stable \
    -o jsonpath='pvc_uid_after={.metadata.uid} pv={.spec.volumeName}{"\n"}'
}

storage_report() {
  python3 - "$1" <<'PY'
import glob, hashlib, os, sqlite3, sys
root = sys.argv[1]
for path in sorted(glob.glob(root + '/**/*.sqlite', recursive=True)):
    con = sqlite3.connect(f'file:{path}?mode=ro', uri=True, timeout=3)
    con.execute('pragma query_only=on')
    events = 0
    tags = 0
    tenants = []
    types = []
    tables = {row[0] for row in con.execute("select name from sqlite_master where type='table'")}
    if 'events' in tables:
        events = con.execute('select count(*) from events').fetchone()[0]
        types = con.execute('select type,count(*) from events group by type order by type').fetchall()
    if 'event_tags' in tables:
        tags = con.execute('select count(*) from event_tags').fetchone()[0]
    if 'tenants' in tables:
        tenants = [row[0] for row in con.execute('select id from tenants order by id')]
    prefixes = ','.join(sorted(hashlib.sha256(x.encode()).hexdigest()[:16] for x in tenants))
    kind_summary = ','.join(f'{name}={count}' for name,count in types)
    print(f'{os.path.relpath(path, root)} events={events} event_tags={tags} tenants={len(tenants)} tenant_sha256_prefixes={prefixes} event_types={kind_summary}')
    con.close()
PY
}

migrate_storage() {
  preflight
  local old_pv node_path source_root stable_root before copied after before_identity after_identity
  old_pv="$(kubectl --context "$CONTEXT" -n "$NAMESPACE" get pvc grain-data -o jsonpath='{.spec.volumeName}')"
  [[ -n "$old_pv" ]] || die "old dynamic candidate PVC is not bound"
  node_path="$(kubectl --context "$CONTEXT" get pv "$old_pv" -o jsonpath='{.spec.local.path}')"
  [[ "$node_path" == /var/lib/rancher/k3s/storage/* ]] || die "candidate PV is not under the dedicated host-backed k3d storage root"
  source_root="${SUPPORT}/storage/$(basename "$node_path")/grain-candidate"
  stable_root="${SUPPORT}/storage/awp-v2-grain-candidate"
  [[ -d "$source_root" ]] || die "candidate Grain source directory is missing"
  [[ ! -e "$stable_root" ]] || die "stable candidate directory already exists; inspect before retrying"

  storage_report "$source_root"
  kubectl --context "$CONTEXT" -n "$NAMESPACE" scale statefulset/grain --replicas=0
  kubectl --context "$CONTEXT" -n "$NAMESPACE" wait --for=delete pod/grain-0 --timeout=180s
  mkdir -m 700 "$stable_root"
  cp -a "$source_root" "$stable_root/grain-candidate"
  before="$(storage_report "$source_root")"
  copied="$(storage_report "$stable_root/grain-candidate")"
  [[ "$before" == "$copied" ]] || die "cold copy does not match source database counts and identities"

  kubectl --context "$CONTEXT" apply -f "$MANIFEST"
  kubectl --context "$CONTEXT" -n "$NAMESPACE" wait --for=jsonpath='{.status.phase}'=Bound pvc/grain-data-stable --timeout=120s
  kubectl --context "$CONTEXT" -n "$NAMESPACE" rollout status statefulset/grain --timeout=240s
  after="$(storage_report "$stable_root/grain-candidate")"
  before_identity="$(printf '%s\n' "$before" | sed -n 's/.*tenant_sha256_prefixes=\([^ ]*\).*/\1/p' | head -n 1)"
  after_identity="$(printf '%s\n' "$after" | sed -n 's/.*tenant_sha256_prefixes=\([^ ]*\).*/\1/p' | head -n 1)"
  [[ -n "$before_identity" && "$before_identity" == "$after_identity" ]] || die "Grain tenant identity did not survive cold migration"
  printf '%s\n' "$after"
  kubectl --context "$CONTEXT" -n "$NAMESPACE" get pvc grain-data-stable \
    -o jsonpath='pvc_uid={.metadata.uid} pv={.spec.volumeName}{"\n"}'
  kubectl --context "$CONTEXT" get pv awp-v2-grain-candidate \
    -o jsonpath='reclaim={.spec.persistentVolumeReclaimPolicy} path={.spec.hostPath.path}{"\n"}'
  kubectl --context "$CONTEXT" -n "$NAMESPACE" delete pvc grain-data --wait=true
}

probe() {
  local port="19447" log="/private/tmp/awp-v2-slice43-grain-port-forward.log" pid="" response=""
  kubectl --context "$CONTEXT" -n "$NAMESPACE" port-forward \
    --address 127.0.0.1 "service/grain" "${port}:9447" >"$log" 2>&1 &
  pid="$!"
  for _ in $(seq 1 30); do
    if response="$(curl --silent --fail --max-time 2 "http://127.0.0.1:${port}/health")"; then
      printf '%s' "$response"
      printf '\n'
      kill "$pid" >/dev/null 2>&1 || true
      wait "$pid" >/dev/null 2>&1 || true
      return 0
    fi
    sleep 1
  done
  kill "$pid" >/dev/null 2>&1 || true
  wait "$pid" >/dev/null 2>&1 || true
  cat "$log" >&2
  die "Grain GET /health did not respond through loopback-only port-forward"
}

status() {
  preflight
  kubectl --context "$CONTEXT" -n "$NAMESPACE" get statefulset,pods,pvc,service -o wide
  kubectl --context "$CONTEXT" get service -n "$NAMESPACE" grain \
    -o jsonpath='service_type={.spec.type} cluster_ip={.spec.clusterIP} ports={.spec.ports[*].port}{"\n"}'
  probe
}

case "${1:-}" in
  build) preflight; build_image ;;
  up) up ;;
  status) status ;;
  restart) preflight; rollout ;;
  migrate-storage) migrate_storage ;;
  *) die "usage: $0 {build|up|status|restart|migrate-storage}" ;;
esac
