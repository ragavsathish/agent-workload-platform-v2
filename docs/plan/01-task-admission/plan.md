# Slice 01: task admission

Cites [tasking domain rules](../../wiki/tasking.md) and the [source inventory](../../../raw/v1/manifest.md).

## Goal

An owner can submit bounded work with a stable request identity, recover the
same task identity after an uncertain response, and inspect the accepted task.

## Scope

Admission, canonical input, duplicate and conflict handling, owner isolation,
eligibility refusal, and accepted-task query. Execution and progress are later
slices. The current v1 source supports ordinary task admission and owner-side
deduplication; stable caller identity is an agreed contract completed here.

## Acceptance

All six named scenarios in [spec.feature](spec.feature) pass through the slice
interface. A simultaneous duplicate must produce one accepted task. A refused
request must leave the accepted task set unchanged.

## Tickets

- [Ticket 01](ticket-01-admission.md): decide and expose admission and query.
- [Ticket 02](ticket-02-verification.md): ratify the scenarios and regeneration
  boundary.

## Open scope

The task lifecycle, storage durability, data import, delivery, and cutover need
later slices. This plan asserts no parity for those outcomes. Decisions made
while building this slice are logged in [decisions.md](decisions.md).
