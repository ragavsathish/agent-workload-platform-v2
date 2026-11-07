# Slice 06: public owner ingress and service lifecycle

Cites [public deployment rules](../../wiki/public-deployment.md), [deployed voice rules](../../wiki/deployed-voice-journey.md), [pinned host contract](../../../raw/v1/host-requirements.md), and [decisions](decisions.md).

## Goal and boundary

Operate the current owner conversation and task paths as persistent services behind a private authenticated public entry. Prove a distinct owner device can establish signaling and two-way live media, speak a final utterance, receive audible speech, and read the same saved turn after a service restart. Independently schedule accepted tasks and recover them through worker interruption. Reject foreign identity and spoofed authority. Qualify an update and rollback against the same owner journey.

The boundary starts at the external owner entry and includes identity, media reachability, conversation and speech readiness, task scheduling, restart, and rollback. Existing conversation, task, and spoken-turn rules remain authoritative. The [scenarios](spec.feature) are the acceptance contract. Specific topology and setup belong in the [implementation note](../../../impl/public-deployment-notes.md).

## Tickets

- [Ticket 01](ticket-01-owner-entry.md): authenticated owner route, signaling, and reachable media.
- [Ticket 02](ticket-02-services-and-scheduling.md): supervised service start, readiness, worker scheduling, restart, and recovery.
- [Ticket 03](ticket-03-rollout-and-qualification.md): fresh public journey, failure injection, rollback, and evidence ledger.

## Exit evidence

The build records a failing named acceptance run before each ticket is implemented, then passes its scenarios and the independent gate. A public qualification report identifies the actual external origin and distinct peer, authenticated owner, media candidate pair and non-silent received audio, accepted turn and destination, saved history after restart, scheduled task and worker attempt, and rollback version. Record safe identifiers and hashes rather than private speech or credentials. A same-host check is useful diagnostic evidence but cannot satisfy the external media or identity gates. Missing tailnet access, model/provider credentials, or host assets leave their corresponding gates explicitly unqualified; they do not prevent implementing and testing the parts that can run locally. See [public deployment rules](../../wiki/public-deployment.md) and [decisions](decisions.md).
