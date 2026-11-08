# Slice 32: durable compaction protocol and Pi proof contract

Cites the [session compaction plan](../29-session-compaction-reconciliation/plan.md),
the [session acceptance rules](../../wiki/session-orchestration.md), the
[build contract](../../../BUILD-CONTRACT.md), the Pi package declaration and
[runtime lockfile](../../../impl/runtime/pnpm-lock.yaml), and executable
protocol evidence in the [owner route](../../../impl/runtime/products/voice-agent/apps/edge/bot.py),
the [Pi server](../../../impl/runtime/products/voice-agent/apps/pi-agent/voice-agent/voice-http-server.ts),
the [owner client API](../../../impl/runtime/products/voice-agent/apps/client/lib/api_client.dart),
and the [Pi package declaration](../../../impl/runtime/products/voice-agent/apps/pi-agent/package.json).

## Goal

Make the current v2 owner-client-to-event-store-to-Pi compaction protocol reproducible from
durable architecture docs and accurately distinguish current implementation
evidence from what the implementation-fenced clean-room run could reconstruct.

## Boundary

This slice records the implemented wire and branch proof contract; it does not
change runtime behavior. The owner event store remains authoritative for AWP command identity,
owner, selected conversation, baseline cursor, and command status. Pi remains
authoritative for its persisted branch and compaction entry. The client may
resume only an owner-visible active command identity. These statements are
grounded in current v2 implementation, pinned dependency declarations, and
tests. They do not claim an atomic multi-call Pi snapshot or exact provider
token accounting.

## Acceptance

- The durable protocol records the `GET /api/pi-context` read and
  `POST /api/pi-context/compact` command, `x-awp-command-id` propagation, and
  the owner-visible context fields required to recover an active request.
- The context fields used by the client are `compactionCommandId`,
  `compactionStatus`, `compactionBaselineLeafId`, and
  `compactionBaselineEntryCount`, alongside the safe usage/eligibility fields.
- The public Edge route forwards to the broker's `/v1/pi/context` routes, which
  proxy the Pi agent's `/v1/context` and `/v1/context/compact`; the command ID
  is forwarded unchanged into the owner event store's compaction-start command.
- It states that retry and page reload recover the same queued, pending, or
  uncertain identity from owner context; a fresh ID is generated only when
  there is no resumable command, and local identity clears after completion.
- It records the installed Pi package version and the types used by the proof:
  session branch entries have stable `id`/`parentId` links, compaction entries
  have type `compaction`, and the manager exposes branch, leaf, and entry-count
  observations. No atomic snapshot guarantee is added.
- The documented reconciliation proof requires a new active-branch tail
  compaction whose parent is the recorded baseline leaf, together with a count
  greater than the saved baseline. Existing restart/fault-injection tests map
  to this contract and assert no second Pi call.
- The clean-room report separates current-v2 executable coverage from the
  clean-room fence limitation; it does not describe an implementation artifact
  as absent from the full v2 repository.
- The report distinguishes caller-supplied admission values in the
  reconstruction from current v2, where trusted owner state comes from the
  durable projection and owner-tagged event appends use compare-and-swap.
- A live protocol test proves stale queued eligibility is rejected after the
  turn refresh, with no extra Pi compaction entry.
- Current Pi runtime and owner client tests, formal model checks, docs gate, link
  checks, and whitespace checks pass.

## Documents

- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-compaction-protocol.md)
