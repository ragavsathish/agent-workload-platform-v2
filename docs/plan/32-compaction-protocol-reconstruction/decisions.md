# Decisions

Cites the [session compaction rules](../29-session-compaction-reconciliation/plan.md),
the [build contract](../../../BUILD-CONTRACT.md), and the current pinned
runtime/package evidence.

## D121 — Owner context restores the active client command identity

The owner client first queries the owner context. It reuses the command ID
only when the owner event store reports `queued`, `pending`, or `uncertain`; otherwise it
creates a fresh 24-byte random hexadecimal ID. It sends that identity in
`x-awp-command-id` to `POST /api/pi-context/compact`, keeps it through failed
responses, and clears it only after a completed result. After a reload, the
Owner event context restores the active ID, so no separate client-side durable
identity store is required by this implementation.

The client reads and writes the Edge `/api/pi-context` routes. Edge calls the
broker `/v1/pi/context` API, which forwards to the Pi agent's `/v1/context`
routes; compaction sends the command ID unchanged through each hop to the owner
event store's compaction-start command. The recovery fields are `compactionCommandId`,
`compactionStatus`, `compactionBaselineLeafId`, and
`compactionBaselineEntryCount`.

## D122 — Pi compaction proof uses persisted branch evidence

The runtime lock pins `@earendil-works/pi-coding-agent` 1.2.0. Its declarations
expose `SessionManager.getBranch(): SessionEntry[]`, `getLeafId(): string | null`,
and `getEntryCount(): number`; `CompactionEntry` extends `SessionEntryBase`,
carrying its `id` and `parentId`, and has `type: "compaction"`. AWP records the
baseline leaf and total entry count before the effect. Reconciliation accepts
only a new active-branch tail compaction whose parent equals the baseline leaf
and whose current total count exceeds the baseline. The manager observations
are made through separate calls; neither the type declarations nor the tests
establish atomic snapshot semantics.

## D123 — Clean-room limits are evidence limits, not runtime absences

The regeneration fence intentionally removes `impl/` and installed runtime
dependencies. Its inability to derive the HTTP adapter, owner-client recovery
logic, or Pi declarations does not mean they are missing from current v2. The
repository tests and pinned dependency manifest are the authority for what
current v2 implements; the clean-room report remains the authority for what
the fenced inputs alone could regenerate.

## D124 — Current command admission reads trusted owner state

The current session handler projects `:session/state` before deciding a
command. It compares the trusted command actor to the command owner, reads
selection, eligibility, active work, and attachment from that projection, and
tags appends by owner with a compare-and-swap predicate on the projected event
revision. The public acceptance suite checks owner isolation and mutation locks.
The clean-room handler could not reconstruct these seams from the permitted
durable inputs; it approximated some admission facts as caller inputs. Current
runtime source and test evidence are linked by the [Slice 32 plan](plan.md).
