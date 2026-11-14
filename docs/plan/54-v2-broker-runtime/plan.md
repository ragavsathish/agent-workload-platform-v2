# Slice 54: Side-by-side v2 broker runtime

## Outcome

Run v2 Pi and the v2 inference broker on private host ports 9556 and 9553,
alongside the retained legacy listeners on 9446 and 9443. The sidecar uses the
existing k3d Grain authority and its existing owner/token inputs. Semaphore
owns the sidecar lifecycle. Edge and trace-render route to the qualified v2
broker; Flux retains the prior manifest state for rollback.

## Feasibility evidence

- `impl/deployment/stack.py` already has `ServiceSpec` process supervision,
  readiness polling, isolated state directories, authenticated service health,
  configurable broker/Pi ports, and start/stop/status commands.
- `impl/deployment/k3d/grain.sh` contains a loopback-only
  `kubectl --context k3d-awp-v2-ops -n awp-v2 port-forward --address 127.0.0.1
  service/grain 19447:9447` probe. The host path has been confirmed to return
  Grain `/health` HTTP 200. Thus the cluster service can be reached without
  adding a public, LAN, NodePort, or second Grain endpoint.
- `voice-config.ts` requires Pi to reach Grain, Gitea, S3Mock, Oxigraph,
  Prometheus, and Grafana MCP. These are ClusterIP services in the existing
  k3d app/telemetry planes. A sidecar must supervise loopback-only forwards for
  each dependency it uses, not just Grain.
- The v2 broker implements `/v1/pi/tasks/routing` and `/v1/pi/turn/latest`, and
  forwards them to the v2 Pi routes. The legacy host service does not provide
  these v2 handlers.
- `impl/deployment/k3d/semaphore/migration/setup_project.py` can provision
  isolated templates but its current refresh path updates the existing project
  pin. Slice 54 will add source for a dedicated template/project provisioning
  path and will not invoke setup or rewrite the existing pin.

## Design

Add an explicit `AWP_NATIVE_PROFILE=voice-sidecar` to `stack.py`. It selects
only the Grain port-forward(s), Pi, and broker. It never starts Grain,
Gateway, scheduler, or the legacy LaunchAgents. Sidecar state and service env
overlay use separate paths from the active native runtime. Pi and broker bind
loopback to 9556 and 9553, and their internal client URL points to sidecar Pi.
Forwarded cluster dependencies use unique host loopback ports. The helper
reads non-secret runtime settings from the active broker process and the
existing Edge and Grain Kubernetes Secrets into memory. It never copies those
values to an env file, Semaphore variables, Ansible facts, logs, or a generated
plist. Persist only non-secret endpoint and port overrides. If the local Gitea
owner, scoped token, or private workspace repository is absent, the helper
bootstraps them through Gitea's local admin CLI and API.

Semaphore gets a dedicated project for sidecar status/start/stop and readiness.
Its preflight confirms legacy health and exact process ownership. A failed
sidecar readiness check stops only a sidecar supervisor started by that
operation and its owned port-forwards. The existing Grain migration project
and its repository pin remain untouched.

## Gates and boundaries

- Edge cutover is applied by Flux at `main@sha1:250a35bf1beb2096e67a52f147eee4499e161820`.
- Do not deploy, restart, stop, replace, or reconfigure legacy host services.
- Do not create, rotate, or apply Grain owner/token secrets.
- Do not expose host listeners outside loopback.
- Before Edge cutover, require Semaphore verify to prove each forward, Pi and
  broker health, required broker routes, existing Grain authority, and legacy
  health; retain an independently invocable Edge/Flux rollback. Semaphore
  check-ready and idempotent start both passed, with all ten readiness checks
  true. The Edge pod returns HTTP 200 from both broker routes.
- Legacy retirement remains blocked on a reviewed quiescence signal.
- No tests are added or run in this slice, as requested.

## Live failure and recovery

On 2026-11-14, the active Edge still targeted the legacy broker at `:9443`.
The loopback listeners at `:9543` and `:9546` were stale orphan processes
started before `/v1/pi/tasks/routing` and `/v1/pi/turn/latest` were present in
their loaded code. The v2 listeners also had credentials that did not match the
current Edge and Grain Secrets. The Gitea instance also had no owner account or
workspace repository, and the configured Gitea token was rejected. The sidecar
uses non-secret settings from the active broker process and reads the existing
Edge and Grain Secrets into memory. It bootstraps the configured local Gitea
owner, a scoped Gitea token stored as a Kubernetes Secret, and the private
workspace repository. Pi startup also now registers `tea` as Git's credential
helper; `tea login add` authenticated the API but did not authenticate the
subsequent `git clone`. It does not create or rotate Edge, Grain, or Tailscale
credentials. The refreshed sidecar runs on `:9553` and `:9556`, and its broker
routes return HTTP 200 from the Edge pod. Flux applied the source manifest at
`:9553` in revision `250a35b`; the Edge rollout completed with a new ready pod.
PromQL reports zero task-route 4xx increases for the 15-minute window checked
after cutover. The exact HTTP 404 query returned no samples, and the relevant
HTTP 401 series showed zero increase. Semaphore task 2147483557 confirmed all
ten readiness checks; task 2147483556 completed the idempotent start and
confirmed the same checks.
The sidecar was already running, so Semaphore reported `started: false` while
leaving it running and ready. A later Semaphore-only readiness issue came from
an inherited `PYTHONPATH` containing several staged source trees and a child
timeout shorter than sequential probe time. The sidecar now uses a bounded
profile-specific `PYTHONPATH`, parallel readiness probes, and a full-pass
timeout.

## Acceptance

See [`spec.feature`](spec.feature) and [`tickets.md`](tickets.md).
