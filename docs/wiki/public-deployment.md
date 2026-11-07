# Public owner deployment and service lifecycle

Cites the [pinned host contract](../../raw/v1/host-requirements.md), [owner routes](../../raw/v1/traefik-routes.yml), [tailnet setup](../../raw/v1/tailnet-login.py), [service lifecycle](../../raw/v1/voice-makefile), [task worker service](../../raw/v1/service-task-worker.plist), [deployed voice rules](deployed-voice-journey.md), and [slice 06 decisions](../plan/06-public-deployment/decisions.md).

## Owner entry and live media

The owner reaches one private, authenticated public origin. The entry verifies the caller's identity before offering the live connection or any selected history. Only the configured owner may enter; an untrusted caller cannot gain owner authority by sending identity or local-access markers. A separate inner service credential does not establish owner identity. Public voice and selected history share the same owner decision. The [host contract](../../raw/v1/host-requirements.md) and [source routes](../../raw/v1/traefik-routes.yml) establish the v1 boundary; [D20](../plan/06-public-deployment/decisions.md) carries it into this slice.

A successful signaling exchange is only the start of a voice connection. The owner must be able to send and receive live media from a distinct device through a reachable media path. A same-host route or a status page does not prove that path. A failed media path is reported as unavailable; it must not produce a completed spoken turn. The [deployed voice rules](deployed-voice-journey.md) govern turn identity, response audio, and saved history, and [D21](../plan/06-public-deployment/decisions.md) makes independent media reachability an explicit gate.

## Service availability and recovery

The owner-facing service becomes ready only when its required conversation and speech capabilities are reachable. An accepted task remains owned through a service restart. The task executor resumes eligible work or exposes a recoverable state; a process restart must not create a second accepted task or replay a settled turn. The owner may inspect safe status while a dependency is unavailable. See the [task lifecycle rules](task-lifecycle.md), [live conversation rules](live-session-integration.md), [source lifecycle](../../raw/v1/voice-makefile), and [D22](../plan/06-public-deployment/decisions.md).

An accepted task is eventually considered for execution by a running task executor without a manual, per-task fixture. Capacity and eligibility govern when work starts. A task that cannot start remains visible with a reason; a restarted executor reconsiders pending work and preserves the original task identity. The [source task worker service](../../raw/v1/service-task-worker.plist) runs independently of the owner service; [D23](../plan/06-public-deployment/decisions.md) states the required v2 behavior.

## Change and rollback

A service change is accepted only after owner entry, live media, persistent read, and task scheduling have passed through the changed deployment. If the new version fails readiness or the owner journey, return to the last qualified version without discarding owner conversations or accepted tasks. Recovery checks the same public origin and task identities after rollback. See the [source lifecycle](../../raw/v1/voice-makefile), [host contract](../../raw/v1/host-requirements.md), and [D24](../plan/06-public-deployment/decisions.md).
