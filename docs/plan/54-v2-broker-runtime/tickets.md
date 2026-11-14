# Slice 54 tickets

## S54-1 — Add sidecar profile and supervised loopback service forwards

Add a `voice-sidecar` stack profile which starts only the existing Grain,
Gitea, S3Mock, Oxigraph, Prometheus/OTel, and Grafana MCP loopback forwards plus
Pi and broker. Use distinct service ports and isolated state. Make each forward
owned by the same supervisor as Pi/broker and expose a bounded readiness
summary. No duplicate Grain or Gateway.

## S54-2 — Add isolated Semaphore sidecar lifecycle

Add a host-runner Ansible task/template for preflight, start, verify, and stop
of the v2 sidecar. Reuse secret inputs through the already protected dotenvx
environment at runtime. Do not copy secrets into Semaphore project variables,
new files, facts, or task output. Add source to the migration tooling without
running project setup or modifying the current live repository pin.

## S54-3 — Verify v2 route chain while Edge remains unchanged

Readiness must verify all required service forwards, authenticated Pi/broker
health, `/v1/pi/tasks/routing`, `/v1/pi/turn/latest`, the existing Grain
authority, and unchanged legacy listeners. Keep the Edge target untouched
until separately reviewed cutover authorization and rollback criteria are met.

## S54-4 — Qualify and cut over Edge, with independent rollback

After route qualification, update Edge and trace-render broker targets through
Flux, retain the prior image/config, and keep rollback independent of the
legacy services. Live rollout status is recorded after qualification.

## S54-5 — Retire legacy only after quiescence evidence

Keep legacy services running until a reliable active-work signal proves there
are no in-flight or uncertain turns. Otherwise defer retirement.
