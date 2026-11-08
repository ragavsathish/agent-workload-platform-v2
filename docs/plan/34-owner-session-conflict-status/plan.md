# Slice 34: report owner-session conflicts with conflict status

Cites the [live conversation rules](../../wiki/live-session-integration.md),
the [held compaction contract](../33-compaction-uncertainty/plan.md), and the
current [Pi route tests](../../../impl/runtime/products/voice-agent/apps/pi-agent/live-protocol.test.mjs).

## Goal

Preserve the difference between a command refused by current owner-session state
and a failed Pi/provider operation at the voice HTTP boundary.

## Boundary

An owner-session conflict such as a held compaction, active turn, or routing
transition returns a conflict result and never reaches the model. Provider,
Pi execution, and uncertain external-effect failures remain upstream failures.
The response does not disclose private owner or provider details. Streaming
responses retain their established event stream after headers are sent and
carry a safe conflict code in the error event.

## Acceptance

- The internal session client preserves the structured response class and status
  for known session command conflicts.
- A non-stream owner turn refused by the session service returns HTTP 409 with a bounded safe
  detail; the model call count does not increase.
- A streamed turn refused before dispatch emits a safe conflict status/code in
  its error event without a model call.
- A genuine Pi/provider failure still returns HTTP 502; invalid request and
  authentication status behavior remains unchanged.
- Routing-transition conflicts use the same conflict classification.
- Tests map each result class and do not classify arbitrary errors by matching
  free-form message text.

## Documents

- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-session-conflict-status.md)
