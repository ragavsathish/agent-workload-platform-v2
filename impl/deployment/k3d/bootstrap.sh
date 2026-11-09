#!/usr/bin/env bash
set -euo pipefail

# One-command local AWP v2 bootstrap. Requires only Docker and k3d on the host.
# Flux reads the private GitHub repo with a read-only deploy key stored outside
# the checkout. This script never deletes the cluster or persistent storage.

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
STATE="${HOME}/Library/Application Support/AWPv2/k3d"
CLUSTER="awp-v2-ops"
SERVER="k3d-${CLUSTER}-server-0"
API_PORT="6448"
UI_PORT="9780"
FLUX_VERSION="v2.10.0"
FLUX_INSTALL_URL="https://github.com/fluxcd/flux2/releases/download/${FLUX_VERSION}/install.yaml"
FLUX_KEY="${STATE}/flux/github-deploy-key"
SECRET_DIR="${STATE}/secrets"
CREATED_CLUSTER=0
PLATFORM="linux/arm64"
TMP_MANIFEST=""

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "missing required host command: $1"; }
cleanup() { [[ -z "$TMP_MANIFEST" ]] || rm -f "$TMP_MANIFEST"; }
trap cleanup EXIT

preflight() {
  need docker
  need k3d
  docker info >/dev/null 2>&1 || die 'Docker engine is not running'
  if [[ "${1:-source}" == source ]]; then
    [[ -f "$FLUX_KEY" ]] || die "private source key missing: $FLUX_KEY (install the read-only GitHub deploy key with mode 0600)"
    [[ -s "$FLUX_KEY" ]] || die 'private source key is empty'
    chmod 600 "$FLUX_KEY"
  fi
  mkdir -p "$STATE/storage" "$SECRET_DIR" "${STATE}/flux"
  chmod 700 "$STATE" "$STATE/storage" "$SECRET_DIR" "${STATE}/flux"
  case "$(docker info --format '{{.Architecture}}')" in
    arm64|aarch64) PLATFORM='linux/arm64' ;;
    amd64|x86_64) PLATFORM='linux/amd64' ;;
    *) die "unsupported Docker architecture: $(docker info --format '{{.Architecture}}')" ;;
  esac
}

cluster_exists() { docker ps --format '{{.Names}}' | grep -Fxq "$SERVER"; }

ensure_cluster() {
  if cluster_exists; then
    printf 'cluster=awp-v2-ops action=reuse\n'
  else
    if k3d cluster list | awk 'NR>1 {print $1}' | grep -Fxq "$CLUSTER"; then
      k3d cluster start "$CLUSTER"
      printf 'cluster=awp-v2-ops action=start-existing\n'
    else
      k3d cluster create "$CLUSTER" \
        --servers 1 --agents 0 \
        --image rancher/k3s:v1.34.13-k3s1 \
        --api-port "127.0.0.1:${API_PORT}" \
        -p "127.0.0.1:${UI_PORT}:80@loadbalancer" \
        --volume "${STATE}/storage:/var/lib/rancher/k3s/storage@server:0"
      CREATED_CLUSTER=1
      printf 'cluster=awp-v2-ops action=created api=127.0.0.1:%s operator_ui=127.0.0.1:%s\n' "$API_PORT" "$UI_PORT"
    fi
  fi
  local attempt=0
  while [[ "$attempt" -lt 90 ]]; do
    if docker exec "$SERVER" kubectl get nodes >/dev/null 2>&1; then return 0; fi
    sleep 2
    attempt=$((attempt + 1))
  done
  die 'k3d server did not become ready within 180 seconds'
}

k() { docker exec -i "$SERVER" kubectl "$@"; }

random_secret() {
  local target="$1"
  if [[ ! -s "$target" ]]; then
    umask 077
    dd if=/dev/urandom bs=48 count=1 2>/dev/null | base64 | tr -d '\n' >"$target"
    printf '\n' >>"$target"
  fi
  chmod 600 "$target"
}

apply_secret() {
  local namespace="$1" name="$2"
  shift 2
  k create namespace "$namespace" --dry-run=client -o yaml | k apply -f - >/dev/null
  TMP_MANIFEST="$(mktemp "${TMPDIR:-/tmp}/awp-k8s-secret.XXXXXX")"
  chmod 600 "$TMP_MANIFEST"
  {
    cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${name}
  namespace: ${namespace}
type: Opaque
data:
EOF
    while [[ $# -ge 2 ]]; do
      local key="$1" file="$2" encoded
      shift 2
      [[ -s "$file" ]] || die "required secret input is empty: $file"
      encoded="$(base64 <"$file" | tr -d '\n')"
      printf '  %s: %s\n' "$key" "$encoded"
      unset encoded
    done
  } >"$TMP_MANIFEST"
  k apply -f - <"$TMP_MANIFEST" >/dev/null
  rm -f "$TMP_MANIFEST"
  TMP_MANIFEST=""
}

ensure_secret() {
  local namespace="$1" name="$2"
  shift 2
  if ! k -n "$namespace" get secret "$name" >/dev/null 2>&1; then
    apply_secret "$namespace" "$name" "$@"
  fi
}

prepare_secrets() {
  local grain_token="${SECRET_DIR}/grain-session-token"
  local owner="${SECRET_DIR}/owner-identity"
  local gateway="${SECRET_DIR}/gateway-access-token"
  local grafana="${SECRET_DIR}/grafana-admin-password"
  local mcp="${SECRET_DIR}/grafana-mcp-caller-token"
  local semaphore_password="${STATE}/semaphore/bootstrap-admin-password"
  local semaphore_runner="${STATE}/semaphore/runner-registration-token"
  local semaphore_username="${STATE}/semaphore/username"
  local semaphore_fullname="${STATE}/semaphore/fullname"
  local semaphore_email="${STATE}/semaphore/email"
  mkdir -p "${STATE}/semaphore"
  chmod 700 "${STATE}/semaphore"
  if ! k -n awp-v2 get secret grain-session >/dev/null 2>&1; then
    random_secret "$grain_token"
    if [[ ! -s "$owner" ]]; then printf 'local-owner@localhost\n' >"$owner"; fi
    chmod 600 "$owner"
    apply_secret awp-v2 grain-session token "$grain_token" owner "$owner"
  fi
  if ! k -n awp-v2 get secret gateway-access >/dev/null 2>&1; then
    random_secret "$gateway"
    apply_secret awp-v2 gateway-access token "$gateway"
  fi
  if ! k -n awp-v2 get secret grafana-admin >/dev/null 2>&1; then
    random_secret "$grafana"
    apply_secret awp-v2 grafana-admin password "$grafana"
  fi
  if ! k -n awp-v2 get secret grafana-mcp-caller >/dev/null 2>&1; then
    random_secret "$mcp"
    apply_secret awp-v2 grafana-mcp-caller token "$mcp"
  fi
  if ! k -n awp-ops get secret semaphore-admin >/dev/null 2>&1; then
    random_secret "$semaphore_password"
    [[ -s "$semaphore_username" ]] || printf 'awp-operator\n' >"$semaphore_username"
    [[ -s "$semaphore_fullname" ]] || printf 'AWP Operator\n' >"$semaphore_fullname"
    [[ -s "$semaphore_email" ]] || printf 'awp-operator@localhost\n' >"$semaphore_email"
    chmod 600 "$semaphore_username" "$semaphore_fullname" "$semaphore_email"
    apply_secret awp-ops semaphore-admin \
      username "$semaphore_username" \
      fullname "$semaphore_fullname" \
      email "$semaphore_email" \
      password "$semaphore_password"
  fi
  if ! k -n awp-ops get secret semaphore-runner >/dev/null 2>&1; then
    random_secret "$semaphore_runner"
    apply_secret awp-ops semaphore-runner token "$semaphore_runner"
  fi
  import_optional_voice_secrets
}

import_optional_voice_secrets() {
  local broker_token="${SECRET_DIR}/broker-client-token"
  local edge_env="${SECRET_DIR}/voice-edge.env"
  local owner_sample="${SECRET_DIR}/voxlock-owner-sample"
  local native_broker_env="${HOME}/Library/Application Support/PipecatEdge/apps/broker/broker.env"
  # Reuse an already available dotenvx-managed native broker token when the
  # host utility is present. All output is captured directly into a private
  # state file; no credential is written to the terminal or a process argument.
  if [[ ! -s "$broker_token" && -f "$native_broker_env" ]] && command -v dotenvx >/dev/null 2>&1; then
    umask 077
    if (cd "$(dirname "$native_broker_env")" && dotenvx run -f "$native_broker_env" -- /bin/sh -c 'printf %s "$INFERENCE_BROKER_TOKEN"') >"$broker_token" 2>/dev/null; then
      [[ -s "$broker_token" ]] || rm -f "$broker_token"
      [[ ! -e "$broker_token" ]] || chmod 600 "$broker_token"
    else
      rm -f "$broker_token"
    fi
  fi
  if [[ -s "$broker_token" ]]; then
    ensure_secret awp-v2 broker-client token "$broker_token"
  fi
  if [[ -s "$edge_env" ]] && ! k -n awp-v2 get secret voice-edge-settings >/dev/null 2>&1; then
    docker exec -i "$SERVER" kubectl -n awp-v2 create secret generic voice-edge-settings \
      --from-env-file=/dev/stdin --dry-run=client -o yaml <"$edge_env" | k apply -f - >/dev/null
  fi
  if [[ -s "$owner_sample" ]] && ! k -n awp-v2 get secret voxlock-owner-sample >/dev/null 2>&1; then
    docker exec -i "$SERVER" kubectl -n awp-v2 create secret generic voxlock-owner-sample \
      --from-file=voxlock-voice-sample=/dev/stdin --dry-run=client -o yaml <"$owner_sample" | k apply -f - >/dev/null
  fi
}

build_images() {
  local client="${ROOT}/impl/runtime/products/voice-agent/apps/client"
  local voice_root="${ROOT}/impl/runtime/products/voice-agent"
  local flutter_image="ghcr.io/cirruslabs/flutter:3.47.7"
  printf 'images=build-and-import platform=%s\n' "$PLATFORM"
  docker build --platform "$PLATFORM" \
    --file "${ROOT}/impl/deployment/k3d/grain.Containerfile" \
    --tag awp-v2-grain:slice43 "$ROOT"
  docker build --platform "$PLATFORM" \
    --file "${ROOT}/impl/deployment/k3d/gateway.Containerfile" \
    --tag awp-v2-gateway:slice46 "$ROOT"

  if voice_inputs_present; then
    if [[ ! -f "${client}/build/web/main.dart.js" ]]; then
      printf 'voice_assets=build-with-container image=%s\n' "$flutter_image"
      docker run --rm --platform "$PLATFORM" \
        --volume "${client}:/workspace" --workdir /workspace \
        "$flutter_image" \
        /bin/bash -lc './tool/flutterw pub get && ./tool/flutterw build web --wasm --release --dart-define=VOICE_WEB_URL=http://localhost:7860/'
    fi
    docker build --platform "$PLATFORM" \
      --file "${voice_root}/apps/edge/Containerfile" \
      --tag awp-v2-voice-edge:slice47 "$voice_root"
    docker build --platform "$PLATFORM" \
      --file "${voice_root}/apps/trace-render-worker/Dockerfile" \
      --tag awp-v2-trace-render-worker:slice47 "$voice_root"
    k3d image import --cluster "$CLUSTER" \
      awp-v2-voice-edge:slice47 awp-v2-trace-render-worker:slice47
  else
    printf 'voice_images=skipped; voice prerequisites are absent\n'
  fi
  k3d image import --cluster "$CLUSTER" \
    awp-v2-grain:slice43 awp-v2-gateway:slice46
}

apply_flux() {
  printf 'flux=install version=%s\n' "$FLUX_VERSION"
  k apply -f "$FLUX_INSTALL_URL" >/dev/null
  k -n flux-system rollout status deployment/source-controller --timeout=300s >/dev/null
  k -n flux-system rollout status deployment/kustomize-controller --timeout=300s >/dev/null

  local known_hosts
  known_hosts='github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl'
  if ! k -n flux-system get secret awp-v2-source >/dev/null 2>&1; then
    docker exec -i "$SERVER" kubectl -n flux-system create secret generic awp-v2-source \
      --from-file=identity=/dev/stdin --from-literal="known_hosts=${known_hosts}" \
      --dry-run=client -o yaml <"$FLUX_KEY" | k apply -f - >/dev/null
  fi
  k apply -f - <"${ROOT}/impl/deployment/k3d/flux/source.yaml" >/dev/null
  if [[ "$CREATED_CLUSTER" == 1 ]]; then
    apply_semaphore_source
  else
    printf 'semaphore=preserved-existing-state\n'
  fi
  if voice_inputs_present; then
    apply_voice_source
    k -n flux-system patch kustomization awp-v2-voice --type=merge -p '{"spec":{"suspend":false}}' >/dev/null
    printf 'voice=enabled-from-existing-private-inputs\n'
  else
    printf 'voice=degraded broker-client, voice-edge-settings, or voxlock-owner-sample is unavailable; host Pi/MLX/Gondolin remains a Semaphore-managed capability\n'
  fi

  k -n flux-system wait gitrepository/awp-v2 --for=condition=Ready --timeout=300s >/dev/null || \
    die 'Flux could not fetch the private repository; check the read-only deploy key and that main contains the bootstrap manifests'
  k -n flux-system wait kustomization/awp-v2-core --for=condition=Ready --timeout=600s >/dev/null || \
    die 'Flux source is ready but core AWP reconciliation did not become Ready'
  if [[ "$CREATED_CLUSTER" == 1 ]]; then
    k -n flux-system wait kustomization/awp-v2-semaphore --for=condition=Ready --timeout=600s >/dev/null || \
      die 'Semaphore HelmChart did not become Ready on the new cluster'
  fi
  if voice_inputs_present; then
    k -n flux-system wait kustomization/awp-v2-voice --for=condition=Ready --timeout=600s >/dev/null || \
      die 'voice inputs were found but the voice Kustomization did not become Ready'
  fi
  printf 'flux=ready source=awp-v2 path=impl/deployment/k3d\n'
}

voice_inputs_present() {
  k -n awp-v2 get secret broker-client >/dev/null 2>&1 && \
    k -n awp-v2 get secret voice-edge-settings >/dev/null 2>&1 && \
    k -n awp-v2 get secret voxlock-owner-sample >/dev/null 2>&1
}

apply_voice_source() {
  cat <<'EOF' | k apply -f - >/dev/null
apiVersion: kustomize.toolkit.fluxcd.io/v1
kind: Kustomization
metadata:
  name: awp-v2-voice
  namespace: flux-system
spec:
  interval: 5m
  path: ./impl/deployment/k3d/voice
  prune: true
  suspend: true
  dependsOn:
    - name: awp-v2-core
  sourceRef:
    kind: GitRepository
    name: awp-v2
EOF
}

apply_semaphore_source() {
  cat <<'EOF' | k apply -f - >/dev/null
apiVersion: kustomize.toolkit.fluxcd.io/v1
kind: Kustomization
metadata:
  name: awp-v2-semaphore
  namespace: flux-system
spec:
  interval: 5m
  path: ./impl/deployment/k3d/semaphore/flux
  prune: true
  sourceRef:
    kind: GitRepository
    name: awp-v2
EOF
}

status() {
  k get nodes
  k -n awp-v2 get pods,services
  if k get crd gitrepositories.source.toolkit.fluxcd.io >/dev/null 2>&1; then
    k -n flux-system get gitrepositories,kustomizations
  else
    printf 'flux=not-installed\n'
  fi
  if [[ "$CREATED_CLUSTER" == 1 ]] || k -n awp-ops get deployment semaphore >/dev/null 2>&1; then
    printf 'semaphore_url=http://localhost:%s\n' "$UI_PORT"
  fi
}

main() {
  preflight
  ensure_cluster
  prepare_secrets
  build_images
  apply_flux
  status
}

case "${1:-up}" in
  up) main ;;
  status)
    preflight status
    cluster_exists || die "cluster ${CLUSTER} is not running"
    status
    ;;
  *) die "usage: $0 [up|status]" ;;
esac
