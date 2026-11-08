# AWP v2 build contract

This root-level contract survives implementation fences. It records explicit
architecture choices and only the ownership obligations required to rebuild
the system. It contains no credentials, keys, hostnames, or deployment secrets.

## Explicit choices

- Grain is the implementation substrate for durable owner state.
- Sessions use CQRS: commands append durable facts and projections serve reads.
- TypeScript Effect coordinates runtime workflows and typed effects.
- Pipecat provides the voice pipeline.
- Flutter is the owner client.

These choices were explicitly selected for AWP v2; regeneration must not infer
them from the v1 snapshot. Grain owns the AWP session command/event state and
its derived projections. It does not supersede authorities already assigned to
other stores: Pi owns its session history and branch, Git owns repository
content and history, and object storage owns durable blobs where those systems
are used.

## Runtime ownership and minimum obligations

- The authenticated owner identity comes from trusted server command context.
  Client-supplied owner IDs, selected-conversation claims, busy/eligibility
  facts, or lock claims are never authoritative.
- Grain application bootstrap registers the AWP session event schema, command
  handlers, query handlers, and projection consumers before serving session
  traffic. Startup/restart rebuilds session projections from the durable event
  stream before exposing reads or admitting mutations.
- Commands that mutate an owner's session share one owner-scoped serialization
  boundary across turns, selection, task attach/detach, and compaction. Durable
  pending or uncertain external work retains that boundary until it is safely
  settled or explicitly resolved by a defined owner operation.
- Session projections are owner scoped. Context/compaction reads expose only
  the owner-visible status and identifiers defined by
  [`specs/session-orchestration.allium`](specs/session-orchestration.allium);
  they do not expose private reasoning, provider payloads, credentials, or
  another owner's state.
- A queued compaction request retains its command identity and selected
  conversation. After the active turn settles, trusted session state is
  re-read and eligibility is revalidated before a pending fact is committed
  and Pi is called. If eligibility is stale, the queue settles as rejected,
  releases its owner lock, and never invokes Pi.

## Evidence boundaries

The pinned [ORC snapshot](raw/orc/manifest.md) is evidence for Grain substrate
patterns only: Grain command/query handler forms, event construction and
reading, event tags, and append-time compare-and-swap patterns. It does not
specify AWP's session domain, owner authorization, lock scope, event schema,
projection behavior, or client protocol.

AWP session invariants and observable outcomes are defined by
[`specs/session-orchestration.allium`](specs/session-orchestration.allium),
the stack-free [session wiki](docs/wiki/session-orchestration.md), and slice
acceptance scenarios. The pinned [v1 source inventory](raw/v1/manifest.md) is
historical evidence for existing product behavior and API names; it does not
override v2 decisions.
