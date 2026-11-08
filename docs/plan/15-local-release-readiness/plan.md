# Slice 15: supervised local release readiness

Cites [local release rules](../../wiki/local-release-readiness.md),
[source decision wall](../../../raw/v1/expanded-storming.md),
[source inventory](../../../raw/v1/manifest.md), and
[slice decisions](decisions.md).

## Goal and boundary

Start one selected v2 version under supervision with fresh private state and
prove its current owner journeys through real service boundaries. The
[scenarios](spec.feature) cover readiness, selected spoken conversation,
delegated task and work product, reviewed knowledge and category relation,
approved song and private audio, managed incident investigation, restart and
recovery, owner isolation, and honest reporting when a dependency is absent.
The local journey begins at service start and ends with an attributable
evidence report and an accurate status page. It does not authorize switching
the v1 deployment or declaring external reachability.

## Source-backed scope

The pinned [decision wall](../../../raw/v1/expanded-storming.md) marks
conversation (D1), task admission and outcome (D2–D3), knowledge and daily
account (D4–D5), personal song (D6), and incident investigation (D7) as
current paths. Owner review in D8 is current; the cross-path selector is
proposed. D9–D11 include current task, manual pull request, and deployment
steps but their shared qualification decisions are proposed. D12–D15 are
proposed component regeneration, candidate selection, and learning paths.
This slice qualifies the implemented current paths, records the manual and
external boundaries, and does not invent the proposed paths.

| Journey | Pinned source | Local evidence required |
| --- | --- | --- |
| Conversation and spoken turn | [conversation service](../../../raw/v1/voice-agent-service.ts), [media probe](../../../raw/v1/smallwebrtc_voice.py) | Selected identity, accepted turn, audible reply, restart read |
| Delegated work and download | [task owner](../../../raw/v1/durable-owner.ts), [artifact store](../../../raw/v1/task-artifacts.py) | One attempt, selected workspace, captured bytes, owner retrieval |
| Personal knowledge and hierarchy | [knowledge contract](../../../raw/v1/c3-personal-knowledge.feature), [source ontology](../../../raw/v1/knowledge-ontology.ttl) | Pending, review, current read, relation, replay |
| Personal song | [song service](../../../raw/v1/song-service.py), [song worker](../../../raw/v1/song-worker.py) | Exact approval, separate render, owner playback |
| Managed incident | [incident contract](../../../raw/v1/c4-operational-incidents.feature), [monitor](../../../raw/v1/stack-alert-monitor.py) | Managed alert, stable task, bounded outcome, owner read |
| Public and manual boundary | [host contract](../../../raw/v1/host-requirements.md), [soundtrack guide](../../../raw/v1/song-pr-workflow.md) | Named unqualified external and manual gates |

## Tickets

- [Ticket 01](ticket-01-supervised-start.md): preflight, isolated state,
  supervised start, and honest readiness.
- [Ticket 02](ticket-02-owner-journeys.md): real local owner journeys through
  conversation, task, knowledge, song, and incident boundaries.
- [Ticket 03](ticket-03-restart-and-isolation.md): restart, recovery,
  idempotency, and cross-owner refusal.
- [Ticket 04](ticket-04-report-and-status.md): evidence ledger, status
  correction, independent gate, and scope limits.

## Exit evidence

Start each named scenario red before implementation. Run a fresh supervised
release with private test state and identify every actual dependency used.
The local report must contain the selected version, state identity, process
and journey identifiers, relevant hashes, timestamps, outcomes, and failures.
Independently inspect the report against source state and owner reads. Run the
repository verifier and clean-room fence after implementation. A missing
service or inaccessible model leaves its journey unavailable; a passing unit
suite does not substitute for a real owner journey. The status page must name
implemented slices through this slice and keep the external gates open unless
their own evidence exists.
