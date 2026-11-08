# Slice 30: session compaction regeneration contract

This slice closes the documentation gaps recorded by the independent
[Slice 29 regeneration delta](regeneration-delta.txt). It aligns
Slice 29 behavior language and records durable build constraints so a fenced
regeneration can recover the selected stack and the minimum runtime contracts.

## Goal

Make the Slice 29 source documentation sufficient to regenerate its intended
behavior and architecture without `impl/` artifacts.

## Boundary

This slice edits only durable documentation and acceptance contracts. Session
behavior descriptions in `docs/wiki` and `docs/plan` remain implementation
agnostic. The formal session contract may state the same observable rules;
session semantics remain defined by that contract and repository acceptance
docs. Pinned source snapshots provide historical or substrate evidence only.

## Acceptance

- Active-turn compaction queues the same command identity until turn settlement;
  it is promoted only after the turn is settled and revalidated. No Pi effect
  occurs while the turn is active.
- Slice 29 plan, scenario, wiki, and formal session contract agree on the
  queue/promote and stale-context rejection flow.
- Slice 29 plan and ticket cite durable `raw/` evidence and the persistent
  root-level [build contract](../../../BUILD-CONTRACT.md), never deleted `impl/`
  files.
- The build contract preserves the selected architecture and records only the
  runtime ownership, bootstrap, projection, and lock requirements needed for
  regeneration; it contains no secrets.
- The build contract distinguishes pinned substrate evidence from AWP session
  rules specified by the formal session contract.
- Scenario IDs are checked against existing acceptance tests, the formal
  contract, docs gates, and the session acceptance suite; `git diff --check`
  passes.

## Documents

- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-durable-regeneration-contract.md)
