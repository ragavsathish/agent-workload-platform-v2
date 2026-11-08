# Slice 26: recoverable task admission

Cites [the task request rules](../../wiki/tasking.md), [the original admission
slice](../01-task-admission/plan.md), the [owner workspace
contract](../24-owner-client/spec.feature), and the [pinned source
inventory](../../../raw/v1/manifest.md).

## Goal

Let an owner recover one accepted durable task after the acceptance response is
lost. A separate intentional submission remains a separate request, even when
its instruction is the same.

## Boundary

The owner client retains one pending request identity for the canonical input
until it receives a successful task response. The existing task authority
continues to decide ownership, workspace eligibility, canonical input, request
identity conflicts, and task identity. This slice does not add another task
store or change task execution, retry, progress, or artifact behavior.

## Acceptance

- A retry after an uncertain response sends the same request identity and
  canonical input and recovers the accepted task.
- A deliberate new submission after a confirmed response sends a new request
  identity and may create a distinct task.
- Reusing one pending identity with changed input does not occur in the client.
- The owner receives a bounded error when admission cannot be confirmed; the
  client retains its pending identity so the owner can retry.

## Verification

Use a gateway protocol double that records one task admission but drops its
response. Retry from the owner workspace, verify that one task identity is
recovered, then submit the same instruction deliberately and verify that the
new request has a different identity. Keep the admission scenarios and owner
checks as the source of truth for server-side behavior.

## Documents

- [Behavior scenarios](spec.feature)
- [Build ticket](ticket-01-owner-retry.md)
