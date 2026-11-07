# Slice 04: live owner conversation integration

Cites [live conversation rules](../../wiki/live-session-integration.md), [conversation orchestration](../../wiki/session-orchestration.md), [slice 03 decisions](../03-session-orchestration/decisions.md), and the [pinned source inventory](../../../raw/v1/manifest.md).

## Goal and vertical boundary

An authenticated owner can start or resume a conversation, send a typed or spoken turn, see its safe live and saved result, stop an in-flight turn, and attach a durable task for a following turn through the current public controls. The selected conversation and its command facts govern routing. The production conversation and task capabilities carry the turn. The service-owned tool environment survives conversation changes.

The boundary starts at the public owner routes and live voice connection, passes through the bounded inference path, and ends at the persistent conversation or task reply. It includes the catalog, history, transcript, context, selection, turn, stop, and detach controls needed for that journey. The [acceptance scenarios](spec.feature) lock public behavior. The build proves the path through its production boundaries with only the external model and speech providers replaced by deterministic protocol drivers; it must not substitute a fake conversation adapter for the production path.

## Command, fact, and observation flow

| Input | Decision and recorded fact | External effect and public observation |
| --- | --- | --- |
| Fresh or resume | Selection accepted with one identity; prior attachment released | Selected saved context opens; catalog and transcript reflect that identity; no tool environment recreation |
| Typed or spoken turn | Turn accepted with fixed conversation or task destination | One prompt reaches the chosen conversation; safe deltas and result update the live read; settled result is inspectable |
| Attach task | Attachment accepted for an eligible owned task | Following prompt reaches that task's own conversation; task identity remains independently inspectable |
| Stop or disconnect | Interruption requested for the accepted turn | Current destination receives abort; final outcome or uncertainty is recorded; selection remains |
| Context read or maintenance | Usage observed or maintenance requested | Current selected context supplies bounded usage; maintenance completion refreshes it |

The owner must never see a committed success for an effect that failed to bind to the selected context. Retried command identities converge. Recovery inspects accepted but unsettled turns and does not replay external effects. Source behavior and deliberate additions are separated in [decisions](decisions.md).

## Tickets

- [Ticket 01](ticket-01-public-path.md): authenticated public routes, bounded broker path, selection and query mapping, and restart recovery.
- [Ticket 02](ticket-02-live-turn.md): spoken and typed production turns, streaming observations, stop and context maintenance.
- [Ticket 03](ticket-03-task-and-journey.md): task attachment, persistent tool environment, disconnect cleanup, and end-to-end verification.

## Exit evidence

Every scenario gets a named failing acceptance test before implementation. A full journey enters through the owner-facing controls, includes a spoken turn through a live voice connection, and observes the selected saved transcript after a fresh process start. Independent checks prove credentials, bounded errors, single dispatch on retry, interruption, task routing, and one persistent tool environment. Provider drivers may control only speech and model outputs; all routing, route, and lifecycle components on the production path run for real. The [implementation note](../../../impl/live-session-integration-notes.md) records the chosen topology and verification commands.
