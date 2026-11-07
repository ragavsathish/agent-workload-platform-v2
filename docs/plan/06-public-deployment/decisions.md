# Slice 06 decisions

Cites [public deployment rules](../../wiki/public-deployment.md), [deployed voice rules](../../wiki/deployed-voice-journey.md), [host contract](../../../raw/v1/host-requirements.md), [source owner routes](../../../raw/v1/traefik-routes.yml), [source task worker service](../../../raw/v1/service-task-worker.plist), and [slice 05 decisions](../05-deployed-voice-journey/decisions.md).

## D20: one externally verified owner authority

Carry forward v1's exact single-owner entry. The public entry derives identity from its trusted connection, strips caller-supplied identity and local-access markers, and authorizes before signaling or reads. A service credential alone grants no owner access. The pruned branch accepted a browser-provided identity or treated the local owner shortcut as public proof. See the [host contract](../../../raw/v1/host-requirements.md) and [source owner routes](../../../raw/v1/traefik-routes.yml).

## D21: qualify signaling and media separately

A successful offer or owner page does not prove that live media can cross the external path. The public gate uses a separate device, records the selected media path, sends timed audio, and receives non-silent response audio. If direct media is unavailable, the build may choose a bounded relay path, but its identity and reachability are measured. The pruned branch reused same-host media as a public pass. See the [pinned voice probe](../../../raw/v1/smallwebrtc_voice.py) and [slice 05 decisions](../05-deployed-voice-journey/decisions.md).

## D22: supervised services preserve authority

Service startup, restart, and health must be independent of a foreground qualification fixture. Required dependencies become ready before public readiness is declared. Persistent conversation, task, and identity state survive process restart; an unknown outcome remains inspectable rather than replayed. The pruned branch treated a launched process and open port as complete recovery. See the [source lifecycle](../../../raw/v1/voice-makefile) and [task lifecycle rules](../../wiki/task-lifecycle.md).

## D23: task scheduling is a deployed capability

An independent executor observes eligible accepted work and starts bounded attempts according to capacity. It survives restart, reconsiders pending work, and does not double-run a claimed attempt. A seeded task plus a manually started worker proves execution plumbing only. See the [source task worker service](../../../raw/v1/service-task-worker.plist) and [task lifecycle rules](../../wiki/task-lifecycle.md).

## D24: rollback preserves accepted work

A deployment update has an explicit previous qualified version. On readiness or journey failure, restore that version while retaining owner state and accepted work, then rerun public owner and task probes. No rollback claim follows from stopping the new process alone. See the [source lifecycle](../../../raw/v1/voice-makefile) and [host contract](../../../raw/v1/host-requirements.md).

## Open: external prerequisites

The current host has no installed tailnet client or configured v2 public route per the [slice 05 implementation note](../../../impl/deployed-voice-journey-notes.md). Owner account and device access, a reachable media path, and deployed model/provider credentials remain external setup. The build must report which prerequisites were present and which public scenarios actually ran. The [implementation note](../../../impl/public-deployment-notes.md) records candidate topology and evidence commands.
