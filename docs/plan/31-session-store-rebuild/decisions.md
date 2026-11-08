# Decisions

Cites the [build contract](../../../BUILD-CONTRACT.md),
[session contract](../../wiki/session-orchestration.md), and
[Slice 30 regeneration report](../30-session-compaction-regeneration/regeneration-delta.txt).

## D119 — Existing bootstrap must be verified through the public system API

The current session API already starts the durable event store and projection
cache, loads the declared schemas, commands, event model, queries, and read
model, and runs the event-model validator. Slice 31 adds executable evidence
through that API; it must not introduce duplicate service registration or an
in-memory replacement. The selected implementation substrate remains recorded
in the root build contract.

## D120 — Reopen evidence must query rebuilt owner projections

A close/reopen test on the same storage directory is the repository's
deterministic restart boundary. Assertions must cover both reconstructed owner
state and startup recovery of ephemeral or unfinished work. External process
deployment and real network restart qualification remain separate runtime
gates.
