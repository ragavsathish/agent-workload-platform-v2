# Slice 03: current conversation orchestration

Cites [conversation orchestration](../../wiki/session-orchestration.md),
[task lifecycle](../../wiki/task-lifecycle.md), and the
[pinned v1 inventory](../../../raw/v1/manifest.md).

## Goal

Recreate the current owner's selected conversation and turn orchestration as
command decisions, recorded facts, and owner-scoped read models. A voice or
typed turn reaches the same selected conversation; saved history remains
inspectable and resumable. Conversation selection never changes the persistent
tool environment's ownership or a durable task's identity.

## Scope and boundary

The aggregate owns active conversation selection, turn admission and outcome,
and task attachment routing. It does not own the tool environment, task
execution, speech transport, or a browser connection. Its commands are Start
Fresh Conversation, Resume Conversation, Submit Turn, Stop Turn, Compact
Context, Attach Task, and Detach Task. It records accepted selections,
turn routing and outcomes, interruption requests, context changes, and
attachment changes. It exposes Active Transcript, Historical Transcript,
Conversation Catalog, Context Usage, and Active Routing queries.

The [acceptance scenarios](spec.feature) are the behavioral lock. Build tests
from those scenarios while red, then implement a public module interface and
verify its owner journey independently after a fresh start. One controlled
turn driver is enough for the first executable proof; it must support delayed,
streamed, failed, and interrupted answers. Source behavior that remains
ambiguous is labeled in [decisions.md](decisions.md).

The existing owner contract keeps fresh and resume returning the selected
identity, list returning bounded summaries, and active or historical reads
returning revisioned safe transcript entries. The named operations and fields
are in the [conversation rules](../../wiki/session-orchestration.md). The
owner journey must use these public operations rather than a projection's
internal state.

## Command, fact, and query map

| Command or observation | Recorded fact | Read model and owner query |
| --- | --- | --- |
| Start Fresh or Resume Saved | Conversation Selected; Task Attachment Released | Active Selection, Conversation Catalog, Active Transcript |
| Submit Turn | Turn Accepted with destination identity | Active Routing, Active Transcript |
| Turn text, tool status, or outcome observed | Safe Transcript Item Recorded; Turn Settled | Active or Historical Transcript, Context Usage |
| Stop Turn or stream disconnect | Turn Interruption Requested; eventual Turn Settled or Uncertain | Active Routing and Transcript; selection stays put |
| Compact Context | Context Compaction Requested; Context Usage Observed | Context Usage |
| Attach or Detach Task | Task Attachment Changed or Released | Active Routing; task identity remains with tasking |

Refused commands do not publish a successful selection or turn fact. The
selected identity and fact revision govern overlapping decisions. A projection
can be rebuilt without invoking model or tool effects. The catalog and both
transcript queries apply the owner visibility and bounds from the
[conversation rules](../../wiki/session-orchestration.md).

## Tickets

- [Ticket 01](ticket-01-selection-and-history.md): selection command facts,
  persisted histories, catalog, owner isolation, public contract shape, and
  fresh-start restoration.
- [Ticket 02](ticket-02-turns-and-projections.md): voice and typed turns,
  serial execution, public transcript, context, and interruption.
- [Ticket 03](ticket-03-attachment-and-verification.md): attachment routing,
  release on selection, tool-environment boundary, and independent journey.

## Exit evidence

Each scenario has a named failing acceptance test before implementation.
Commands and facts produce the documented query views; a fresh start restores
the same selected identity and safe historical transcript. An overlapping
selection cannot redirect an in-flight turn. Duplicate requests with the
same command identity converge; conflicting reuse refuses. A missing or
other-owner conversation reveals no history. The independent journey proves
that a conversation switch does not recreate the tool environment or the
durable task. Every difference is recorded in [decisions.md](decisions.md).

## Deferred

Speech capture and playback, model/provider selection, production tool calls,
and task execution belong outside this slice. Production turn side-effect
reconciliation requires a later adapter contract. The first build proves
the domain orchestration with a controlled turn driver.
