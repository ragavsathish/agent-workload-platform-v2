#!/usr/bin/env bash
set -euo pipefail

# Enroll the k3d voice Edge into the owner's tailnet and open its private URL.
# The Tailscale key is read without echo and sent only through kubectl stdin.
CLUSTER="awp-v2-ops"
SERVER="k3d-${CLUSTER}-server-0"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || die 'Docker is required'
docker exec "$SERVER" kubectl get nodes >/dev/null 2>&1 || die 'k3d cluster awp-v2-ops is not running; run impl/deployment/k3d/bootstrap.sh up first'
for required_secret in broker-client gateway-access grafana-mcp-caller voice-edge-settings voxlock-owner-sample; do
  docker exec "$SERVER" kubectl -n awp-v2 get secret "$required_secret" >/dev/null 2>&1 ||
    die "required voice input $required_secret is missing; bootstrap the host-backed voice prerequisites first"
done

printf 'Tailnet owner login (email): '
IFS= read -r owner_login
[[ "$owner_login" == *@* ]] || die 'enter the email shown as your Tailscale user login'
printf 'Paste a preauthorized, non-ephemeral Tailscale auth key: '
IFS= read -r -s auth_key
printf '\n'
[[ -n "$auth_key" ]] || die 'Tailscale auth key was empty'

encoded_key="$(printf '%s' "$auth_key" | base64 | tr -d '\n')"
unset auth_key
{
  cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: voice-tailnet-auth
  namespace: awp-v2
type: Opaque
data:
  authkey: ${encoded_key}
EOF
} | docker exec -i "$SERVER" kubectl apply -f - >/dev/null
unset encoded_key
printf 'tailnet_auth=stored-in-kubernetes-secret\n'

docker exec "$SERVER" kubectl -n flux-system patch kustomization awp-v2-voice \
  --type=merge -p '{"spec":{"suspend":false}}' >/dev/null
printf 'voice_flux=resume-requested\n'
docker exec "$SERVER" kubectl -n awp-v2 rollout restart deployment/voice-edge >/dev/null
docker exec "$SERVER" kubectl -n awp-v2 rollout status deployment/voice-edge --timeout=8m

pod="$(docker exec "$SERVER" kubectl -n awp-v2 get pod \
  -l app.kubernetes.io/name=voice-edge -o jsonpath='{.items[0].metadata.name}')"
[[ -n "$pod" ]] || die 'voice Edge pod was not found after rollout'
tailnet_dns=''
for attempt in $(seq 1 60); do
  status_json="$(docker exec "$SERVER" kubectl -n awp-v2 exec "$pod" -c tailscale -- tailscale status --json 2>/dev/null || true)"
  if [[ -n "$status_json" ]]; then
    tailnet_dns="$(printf '%s' "$status_json" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("Self",{}).get("DNSName", ""))' 2>/dev/null || true)"
  fi
  unset status_json
  [[ -n "$tailnet_dns" ]] && break
  sleep 2
done
tailnet_dns="${tailnet_dns%.}"
[[ "$tailnet_dns" == *.ts.net ]] || die 'Tailscale is enrolled but did not report a MagicDNS .ts.net name'
origin="https://${tailnet_dns}"
patch="$(python3 -c 'import json,sys; print(json.dumps({"stringData":{"EDGE_ALLOWED_USERS":sys.argv[1],"PIPECAT_ALLOWED_ORIGINS":sys.argv[2]}}))' "$owner_login" "$origin")"
unset owner_login
docker exec "$SERVER" kubectl -n awp-v2 patch secret voice-edge-settings \
  --type=merge -p "$patch" >/dev/null
unset patch
docker exec "$SERVER" kubectl -n awp-v2 rollout restart deployment/voice-edge >/dev/null
docker exec "$SERVER" kubectl -n awp-v2 rollout status deployment/voice-edge --timeout=8m

printf '\nAWP voice URL: %s/\n' "$origin"
printf 'Access it from a device signed into the same tailnet. Allow microphone access when the app asks.\n'
