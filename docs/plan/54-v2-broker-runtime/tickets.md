# Slice 54 tickets

## S54-1 — Add sidecar profile and supervised loopback service forwards

**Done.** The host sidecar is healthy with isolated ports/state and ten passing
readiness checks. It reuses the existing Grain authority.

Add a `voice-sidecar` stack profile which starts only the existing Grain,
Gitea, S3Mock, Oxigraph, Prometheus/OTel, and Grafana MCP loopback forwards plus
Pi and broker. Use distinct service ports and isolated state. Make each forward
owned by the same supervisor as Pi/broker and expose a bounded readiness
summary. No duplicate Grain or Gateway.

## S54-2 — Add isolated Semaphore sidecar lifecycle

**Done.** Semaphore project `AWP v2 Voice Sidecar` provides preflight, start,
check-ready, status, and stop templates. Tasks 2147483557 and 2147483556 passed
with all service and route checks true. The existing Grain migration project
pin remains unchanged.

Add a host-runner Ansible task/template for preflight, start, verify, and stop
of the v2 sidecar. Reuse secret inputs through the already protected dotenvx
environment at runtime. Do not copy secrets into Semaphore project variables,
new files, facts, or task output. Keep this project and repository pin isolated
from the existing Grain migration project.

## S54-3 — Verify v2 route chain while Edge remains unchanged

**Done before cutover.** Semaphore verified the forwards, authenticated Pi and
broker, both broker routes, existing Grain, and legacy services. The Edge pod
also returned HTTP 200 for routing and latest-turn through its host path.

Readiness must verify all required service forwards, authenticated Pi/broker
health, `/v1/pi/tasks/routing`, `/v1/pi/turn/latest`, the existing Grain
authority, and unchanged legacy listeners before cutover.

## S54-4 — Qualify and cut over Edge, with independent rollback

**Done.** Commit `250a35b` was applied by Flux; Edge rolled out against
`host.docker.internal:9553`. PromQL showed zero task-route 4xx increases in the
15-minute post-cutover window. Legacy listeners remain available for rollback.

After route qualification, update Edge and trace-render broker targets through
Flux, retain the prior image/config, and keep rollback independent of the
legacy services. Live rollout status is recorded after qualification.

## S54-5 — Retire legacy only after quiescence evidence

**Deferred.** No reliable active-work/quiescence signal is available.

Keep legacy services running until a reliable active-work signal proves there
are no in-flight or uncertain turns. Otherwise defer retirement.
