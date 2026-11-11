# Slice 53: align voice owner identity and broker contract

## Goal

Restore the owner task flow without reassigning persisted Grain records, and make the Edge use the broker routes implemented by the v2 runtime.

## Evidence

- PromQL for the last 30 minutes showed two increases for Edge `GET /api/tasks` 403 and two matching `awp-v2-gateway` `tasks` 4xx responses.
- A redacted live comparison found the active Tailscale login matches `EDGE_ALLOWED_USERS`, while the gateway's `AWP_OWNER_IDENTITY` matches neither. Gateway source returns 403 when the supplied `identity` differs from its configured owner.
- The Edge deployment points at `http://host.docker.internal:9443/v1`. Authenticated in-pod probes reached that host service; `/v1/pi/tasks/routing` and `/v1/pi/turn/latest` returned 404. The v2 broker source implements both routes, but the active 9443 host listener is documented as the v1 broker.
- Grain sessions/tasks are owner-scoped in persisted commands/read models. Replacing `grain-session.owner` without migrating its records could hide existing state. This slice preserves that key and maps only after Tailnet authentication.

## Scope

- Keep Tailnet login as the external authentication principal and the existing Grain owner as the data authorization key.
- Map a single validated Edge principal to the canonical Grain owner when Edge calls the task gateway.
- Fail closed if the Edge owner allowlist is ambiguous or the Grain owner mapping is absent.
- Qualify a v2 broker endpoint for the Edge's existing v2 Pi task-routing and latest-turn calls. Keep the legacy v1 broker available until a side-by-side v2 runtime passes its readiness and rollback checks.
- Keep Tailnet/Edge authentication, the Grain event store, and existing owner-scoped records intact.

## Out of scope

- Rewriting Grain owner fields or event-store tags in place.
- Restarting or replacing the active v1 host broker before native voice quiescence is observable.
- Treating a 404/502 broker response as success or hiding unsupported routes with fallback payloads.

## Acceptance

See [Gherkin scenarios](spec.feature). The slice is complete only when PromQL shows the task gateway no longer returning owner 4xx, direct authenticated Edge-to-v2-broker probes succeed for both required routes, an owner voice/task journey completes, and unauthorized identities remain refused. Record the selected Grain owner unchanged and verify owner-scoped state continuity by counts/hashes only.

## Safety gate

Before any runtime identity change, inspect existing Grain state read-only and preserve its owner key. Any host broker rollout must be a side-by-side v2 instance managed through the reviewed Semaphore path, with a proven idle/quiescence signal, readiness checks, and rollback. The current broker probe does not prove native voice activity is idle; do not restart or replace the active host process until that gate changes.

## Implementation decision

The Tailnet login is a transport principal, while `grain-session.owner` is the persisted data owner. The Edge must authenticate the former against its one-user allowlist, then forward the exact owner value loaded from the existing `grain-session` Secret to the Gateway. This avoids rewriting Grain ownership and preserves the Gateway's exact owner check. A multi-user Edge allowlist fails closed for owner-scoped Gateway calls because mapping several principals to one owner's data would be unsafe.

Read-only source audit confirms the existing Semaphore project has a broker health probe but no side-by-side v2 broker/Pi launch task. Slice 44's `stack.py` lifecycle manages a full native stack and its persistent state; it is not a broker-only sidecar operation. The active native turn has no trustworthy idle signal, so this slice cannot safely replace or restart the :9443 broker. A separate v2 service deployment path and its data/owner wiring remain required before the two broker routes can be made live.
