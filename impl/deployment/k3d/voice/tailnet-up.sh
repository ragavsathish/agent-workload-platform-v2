#!/usr/bin/env bash
set -euo pipefail

# Reuse the old v1 tailnet node state when available; otherwise authenticate
# this node with Tailscale's browser-based device approval flow.
CLUSTER="awp-v2-ops"
SERVER="k3d-${CLUSTER}-server-0"
OLD_STATE_VOLUME="pipecat-mlx-voice_tailscale-state"
TAILSCALE_IMAGE="ghcr.io/tailscale/tailscale:v1.104.4"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || die 'Docker is required'
docker exec "$SERVER" kubectl get nodes >/dev/null 2>&1 || die 'k3d cluster awp-v2-ops is not running; run impl/deployment/k3d/bootstrap.sh up first'
old_state_users="$(docker ps --filter "volume=${OLD_STATE_VOLUME}" --format '{{.Names}}')"
if [[ -n "$old_state_users" ]]; then
  die "a running container still uses $OLD_STATE_VOLUME; stop it before transferring the device state"
fi
for required_secret in broker-client gateway-access grafana-mcp-caller voice-edge-settings voxlock-owner-sample; do
  docker exec "$SERVER" kubectl -n awp-v2 get secret "$required_secret" >/dev/null 2>&1 ||
    die "required voice input $required_secret is missing; bootstrap the host-backed voice prerequisites first"
done

k() { docker exec -i "$SERVER" kubectl "$@"; }
k -n flux-system patch kustomization awp-v2-voice --type=merge -p '{"spec":{"suspend":false}}' >/dev/null
flux_ready=''
for attempt in $(seq 1 480); do
  source_revision="$(k -n flux-system get gitrepository awp-v2 -o jsonpath='{.status.artifact.revision}')"
  voice_revision="$(k -n flux-system get kustomization awp-v2-voice -o jsonpath='{.status.lastAppliedRevision}')"
  flux_ready="$(k -n flux-system get kustomization awp-v2-voice -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}')"
  if [[ "$flux_ready" == True && -n "$source_revision" && "$voice_revision" == "$source_revision" ]]; then
    break
  fi
  sleep 1
done
[[ "$flux_ready" == True && "$voice_revision" == "$source_revision" ]] ||
  die 'Flux did not apply the current public voice deployment revision'

pod="$(k -n awp-v2 get pod -l app.kubernetes.io/name=voice-edge -o jsonpath='{.items[0].metadata.name}')"
[[ -n "$pod" ]] || die 'voice Edge pod was not found'
k -n awp-v2 wait --for=condition=Ready "pod/$pod" --timeout=2m >/dev/null

enrollment_marker="$(k -n awp-v2 exec "$pod" -c tailscale -- sh -c 'test -f /var/lib/tailscale/.enrolled && echo yes || echo no')"

if [[ "$enrollment_marker" != yes ]] && docker volume inspect "$OLD_STATE_VOLUME" >/dev/null 2>&1; then
  old_state="$(docker run --rm --entrypoint /bin/sh --volume "$OLD_STATE_VOLUME:/source:ro" "$TAILSCALE_IMAGE" -ec 'test -s /source/tailscaled.state && echo present || echo absent')"
  if [[ "$old_state" == present ]]; then
    printf 'tailnet_state=transferring-existing-v1-identity\n'
    docker run --rm --entrypoint /bin/sh \
      --volume "$OLD_STATE_VOLUME:/source:ro" "$TAILSCALE_IMAGE" \
      -ec 'tar -C /source -cf - tailscaled.state certs' |
      docker exec -i "$SERVER" kubectl -n awp-v2 exec -i "$pod" \
        -c tailscale -- tar -C /var/lib/tailscale -xf -
    k -n awp-v2 exec "$pod" -c tailscale -- sh -ec \
      'rm -f /var/lib/tailscale/.interactive; touch /var/lib/tailscale/.enrolled'
  fi
fi

enrollment_marker="$(k -n awp-v2 exec "$pod" -c tailscale -- sh -c 'test -f /var/lib/tailscale/.enrolled && echo yes || echo no')"
if [[ "$enrollment_marker" != yes ]]; then
  printf 'tailnet_state=interactive-device-login-required\n'
  k -n awp-v2 exec "$pod" -c tailscale -- sh -ec 'touch /var/lib/tailscale/.interactive'
fi

k -n awp-v2 rollout restart deployment/voice-edge >/dev/null
k -n awp-v2 rollout status deployment/voice-edge --timeout=8m
pod="$(k -n awp-v2 get pod -l app.kubernetes.io/name=voice-edge -o jsonpath='{.items[0].metadata.name}')"
[[ -n "$pod" ]] || die 'voice Edge pod was not found after rollout'

last_auth_url=''
tailnet_dns=''
owner_login=''
for attempt in $(seq 1 600); do
  status_json="$(k -n awp-v2 exec "$pod" -c tailscale -- tailscale status --json 2>/dev/null || true)"
  if [[ -n "$status_json" ]]; then
    backend_state="$(printf '%s' "$status_json" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("BackendState", ""))' 2>/dev/null || true)"
    auth_url="$(printf '%s' "$status_json" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("AuthURL", ""))' 2>/dev/null || true)"
    if [[ "$backend_state" == Running ]]; then
      identity="$(printf '%s' "$status_json" | python3 -c 'import json,sys; d=json.load(sys.stdin); s=d.get("Self",{}); u=d.get("User",{}).get(str(s.get("UserID")),{}); print(s.get("DNSName", "").rstrip(".") + "\t" + u.get("LoginName", "").lower())')"
      tailnet_dns="${identity%%$'\t'*}"
      owner_login="${identity#*$'\t'}"
      unset identity
      if [[ -n "$tailnet_dns" && "$tailnet_dns" == *.ts.net && "$owner_login" == *@* ]]; then
        break
      fi
    elif [[ -n "$auth_url" && "$auth_url" != "$last_auth_url" ]]; then
      last_auth_url="$auth_url"
      if command -v open >/dev/null 2>&1 && open "$auth_url" >/dev/null 2>&1; then
        printf 'tailnet_login=opened-device-approval-in-browser\n'
      else
        printf 'Authenticate the voice node at: %s\n' "$auth_url"
      fi
    fi
  fi
  sleep 1
done
unset status_json auth_url last_auth_url backend_state
[[ "$tailnet_dns" == *.ts.net && "$owner_login" == *@* ]] || die 'Tailscale did not reach Running state within 10 minutes'

k -n awp-v2 exec "$pod" -c tailscale -- sh -ec 'rm -f /var/lib/tailscale/.interactive; touch /var/lib/tailscale/.enrolled'
origin="https://${tailnet_dns}"
patch="$(python3 -c 'import json,sys; print(json.dumps({"stringData":{"EDGE_ALLOWED_USERS":sys.argv[1],"PIPECAT_ALLOWED_ORIGINS":sys.argv[2]}}))' "$owner_login" "$origin")"
unset owner_login
k -n awp-v2 patch secret voice-edge-settings --type=merge -p "$patch" >/dev/null
unset patch
k -n awp-v2 rollout restart deployment/voice-edge >/dev/null
k -n awp-v2 rollout status deployment/voice-edge --timeout=8m

printf '\nAWP voice URL: %s/\n' "$origin"
printf 'Open it from a device signed into this tailnet. Allow microphone access when prompted.\n'
