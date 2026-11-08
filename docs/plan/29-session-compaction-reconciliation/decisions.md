# Decisions

Cites the [session rules](../../wiki/session-orchestration.md), the
[session CQRS contract](../03-session-orchestration/plan.md), and the pinned
Pi dependency API installed from the runtime lockfile.

## D112 — owner event store writes intent before Pi compaction

The owner command identity and selected conversation are committed in owner event store
before calling Pi. A owner event store `pending` fact is the durable effect claim and
session lock. Pi history remains authoritative for the result; event store's context
projection records the result only after a matching appended compaction entry
is observed.

## D113 — A Pi branch cursor proves a completed compaction

The pinned Pi session API exposes branch entry IDs and parent IDs. Slice 29
records the baseline leaf and entry count, then accepts completion only when
the active branch contains a new tail compaction entry parented to that exact
leaf. Any divergent branch or missing evidence remains uncertain and is never
automatically re-applied.

## D114 — Pending compaction blocks owner mutations

Turns, selection changes, and task routing cannot race a compaction effect
whose owner event store outcome is unresolved. This keeps the selected conversation and
branch cursor stable until the command completes or remains explicitly held.

## D115 — Retry identity is transport metadata

The owner client retains one command identity through an uncertain retry and
clears it only after success. The identity is forwarded unchanged through
Edge, Broker, Pi, and owner event store. It does not become session authority in the
client.

If a page reload loses the in-memory key, the owner event store-backed context query
returns the active pending command identity and status. The client may resume
that command; a different new identity cannot bypass the owner lock.
