#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
STATE="${HOME}/Library/Application Support/AWPv2/k3d"
CLUSTER="awp-v2-ops"
NAMESPACE="awp-ops"
CONTEXT="k3d-${CLUSTER}"
UI_PORT="9780"
API_PORT="6448"
CHART_VERSION="16.2.2"
VALUES="${ROOT}/impl/deployment/k3d/semaphore-values.yaml"
SECRETS="${STATE}/semaphore"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"; }
loopback_free() {
  local port="$1"
  ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}
secret_env_value() {
  local key="$1" file="$2"
  sed -n "s/^${key}=//p" "$file" | head -n 1
}

preflight() {
  need docker; need k3d; need kubectl; need helm; need lsof
  docker info >/dev/null 2>&1 || die "Docker engine is not ready"
  if ! k3d cluster list -o json | python3 -c 'import json,sys; d=json.load(sys.stdin); raise SystemExit(0 if any(c.get("name")==sys.argv[1] for c in d) else 1)' "$CLUSTER"; then
    loopback_free "$UI_PORT" || die "loopback port ${UI_PORT} is occupied"
    loopback_free "$API_PORT" || die "loopback port ${API_PORT} is occupied"
  fi
  [[ -f "$VALUES" ]] || die "missing values file: $VALUES"
}

make_secrets() {
  mkdir -p "$SECRETS" "${STATE}/storage"
  chmod 700 "$SECRETS" "${STATE}"
  if [[ ! -f "${SECRETS}/admin.env" ]]; then
    local password
    password="$(openssl rand -base64 36 | tr -d '\n')"
    umask 077
    cat >"${SECRETS}/admin.env" <<EOF
fullname=AWP Operator
username=awp-operator
email=awp-operator@localhost
password=${password}
EOF
  fi
  if [[ ! -f "${SECRETS}/runner-registration-token" ]]; then
    umask 077
    openssl rand -hex 32 >"${SECRETS}/runner-registration-token"
  fi
  chmod 600 "${SECRETS}/admin.env" "${SECRETS}/runner-registration-token"
  chmod 700 "${STATE}/storage"
  for key in fullname username email password; do
    [[ -n "$(secret_env_value "$key" "${SECRETS}/admin.env")" ]] || die "admin.env must define ${key}"
  done
}

secret_apply() {
  local admin_user admin_name admin_email admin_password runner_token
  admin_user="$(secret_env_value username "${SECRETS}/admin.env")"
  admin_name="$(secret_env_value fullname "${SECRETS}/admin.env")"
  admin_email="$(secret_env_value email "${SECRETS}/admin.env")"
  admin_password="$(secret_env_value password "${SECRETS}/admin.env")"
  runner_token="$(cat "${SECRETS}/runner-registration-token")"
  kubectl --context "$CONTEXT" create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl --context "$CONTEXT" apply -f - >/dev/null
  kubectl --context "$CONTEXT" -n "$NAMESPACE" create secret generic semaphore-admin \
    --from-literal=username="$admin_user" --from-literal=fullname="$admin_name" \
    --from-literal=email="$admin_email" --from-literal=password="$admin_password" \
    --dry-run=client -o yaml | kubectl --context "$CONTEXT" apply -f - >/dev/null
  kubectl --context "$CONTEXT" -n "$NAMESPACE" create secret generic semaphore-runner \
    --from-literal=token="$runner_token" --dry-run=client -o yaml | kubectl --context "$CONTEXT" apply -f - >/dev/null
  unset admin_password runner_token
}

up() {
  preflight
  make_secrets
  if ! k3d cluster list -o json | python3 -c 'import json,sys; d=json.load(sys.stdin); raise SystemExit(0 if any(c.get("name")==sys.argv[1] for c in d) else 1)' "$CLUSTER"; then
    k3d cluster create "$CLUSTER" --servers 1 --agents 0 \
      --image rancher/k3s:v1.34.13-k3s1 \
      --api-port "127.0.0.1:${API_PORT}" \
      -p "127.0.0.1:${UI_PORT}:80@loadbalancer" \
      --volume "${STATE}/storage:/var/lib/rancher/k3s/storage@server:0"
  fi
  secret_apply
  if ! helm repo list | awk '$1=="semaphoreui" {found=1} END {exit !found}'; then
    helm repo add semaphoreui https://semaphoreui.github.io/charts
  fi
  helm repo update semaphoreui
  helm upgrade --install semaphore semaphoreui/semaphore \
    --kube-context "$CONTEXT" --namespace "$NAMESPACE" \
    --version "$CHART_VERSION" --values "$VALUES" --wait --timeout 5m
  printf 'Semaphore: http://localhost:%s\n' "$UI_PORT"
  printf 'Admin credentials: %s/admin.env (mode 0600)\n' "$SECRETS"
}

status() {
  preflight
  k3d cluster list
  kubectl --context "$CONTEXT" -n "$NAMESPACE" get pods,pvc,ingress
  curl --silent --show-error --output /dev/null --write-out 'ui_http=%{http_code}\n' "http://localhost:${UI_PORT}/"
  curl --silent --show-error --output /dev/null --write-out 'api_ping_http=%{http_code}\n' "http://localhost:${UI_PORT}/api/ping"
  lsof -nP -iTCP:"$UI_PORT" -sTCP:LISTEN
}

stop() {
  preflight
  if k3d cluster list -o json | python3 -c 'import json,sys; d=json.load(sys.stdin); raise SystemExit(0 if any(c.get("name")==sys.argv[1] for c in d) else 1)' "$CLUSTER"; then
    k3d cluster delete "$CLUSTER"
  fi
  printf 'Cluster stopped; persistent data remains under %s\n' "$STATE"
}

case "${1:-}" in
  up) up ;;
  status) status ;;
  stop) stop ;;
  *) die "usage: $0 {up|status|stop}" ;;
esac
