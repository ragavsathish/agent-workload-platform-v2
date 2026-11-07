# Slice decisions and unresolved evidence

Cites [slice plan](plan.md), [conversation wiki](../../wiki/session-orchestration.md),
and the [pinned source inventory](../../../raw/v1/manifest.md).

## D07: command facts drive read models

The source keeps an active in-process selection alongside saved conversation
history and a live public transcript. This slice records accepted selection,
turn routing and outcomes, and attachment changes as durable facts; active
selection, catalog, transcript, and context are separate read models. The
pruned branch was to let mutable conversation objects become the sole source
for every owner view. Projection recovery must not rerun model or tool effects.

## D08: explicit command identity and uncertainty

The [source routes](../../../raw/v1/voice-http-server.ts) do not accept a
caller-supplied idempotency key. V2 assigns a stable command identity to
owner selection and turn requests. Identical retries return the prior decision;
the same identity with different input is refused. An uncertain turn outcome
is inspectable and is not automatically re-executed. The pruned branch was to
create another conversation or repeat an external turn after a lost response.
This strengthens the source contract without claiming v1 already did it.

## D09: owner scope and separate service authority

The source is an owner-only deployment: the outer gateway authenticates owner
identity and the inner service requires a service credential. V2 partitions
conversation facts and queries by owner, so another caller receives a bounded
missing answer. A service credential cannot stand in for the owner at the
public boundary. The pruned branch was a global catalog shared across owners.

## D10: selection does not own the tool environment or task

The [orchestrator](../../../raw/v1/voice-agent.ts) creates one persistent tool
environment and changes conversation managers inside it. A task attachment
routes turns to a separate task conversation. V2 selection facts cannot create
or destroy the tool environment or change a durable task identity. On selection,
release the attachment as source behavior. The pruned branch was to model
conversation identity as the tool environment or task identity.

## D12: persist the selected identity across restart

The [session catalog source](../../../raw/v1/voice-sessions.ts) opens the most
recent saved history on restart. The [orchestrator](../../../raw/v1/voice-agent.ts)
can resume an older conversation, but that selection is not separately saved;
the same selection after restart is therefore not guaranteed in v1. V2 records
the selection fact and restores its identity after restart. The pruned branch
was to infer selection from a history's modification time, which would lose an
owner's explicit resume choice. This is a deliberate strengthening of the
source behavior behind [session-003](spec.feature).

## D11: preserve live and historical visibility limits

The [public projection](../../../raw/v1/public-transcript.ts) bounds entries
and text and excludes private reasoning and tool payloads; the
[catalog](../../../raw/v1/voice-sessions.ts) caps recent sessions and marks
the active one. V2 retains these observed limits and owner visibility rules.
The pruned branch was to expose raw saved entries as the transcript query.

## Open: attachment recovery after service restart

The [attachment source](../../../raw/v1/remote-task-session.ts) keeps a live
connection and the orchestrator releases it on conversation changes. It does
not prove that an attachment is restored after restart. The first build may
restore the selected orchestrator conversation while requiring the owner to
reattach a task; record that as an observable recovery state, not a silent
replay of the task turn.

The attached task's own continuation contract must deduplicate a routed turn
identity before a production adapter retries it. The orchestrator records the
routing decision, while the task owns its conversation and effect outcome.

## Open: turn effects after disconnect or timeout

The [orchestrator](../../../raw/v1/voice-agent.ts) requests abort on timeout;
the [service route](../../../raw/v1/voice-http-server.ts) requests abort when a
stream disconnects. Neither proves that downstream tool effects have stopped
or can safely be repeated. Production adapters need effect reconciliation.

## Open: exact context projection

The [orchestrator](../../../raw/v1/voice-agent.ts) reports persisted or
estimated usage and compaction eligibility. The controlled build can preserve
the observable fields; exact provider token accounting remains an adapter
question, not a new domain rule.
