# Decisions

Cites the [deployment-level source capture](../../raw/v1/deployment-levels-worktree.md)
and [slice 23 plan](plan.md).

## D98 — Three named levels, full by default

Use `voice`, `durable`, and `full`. An omitted setting means `full`, preserving
the current default deployment.

## D99 — Requested, active, and ready are distinct

Persist the requested level separately from observed active capabilities and
readiness. A failed transition must not report ready merely because a level was
requested.

## D100 — Voice isolation is independent of task execution

The persistent Pi Voice tool environment remains isolated for every level.
Task executor selection does not relax voice isolation.

## D101 — Refuse unavailable task work before acceptance

At `voice`, durable task execution is unavailable. Refuse a new durable task
before creating an accepted task identity.

## D102 — Transitions do not replay interrupted tasks

An interrupted task keeps its identity and enters the existing recovery path.
Only an explicit owner recovery action may resume it.

## D103 — Record deployment decisions and observed effects

Persist requested-level changes and observed transition outcomes as ordered
facts. Starting and stopping services are external effects; readiness follows
observed health, not intent.

## D104 — Selection retries are idempotent

Replaying the same selection identity and level has no additional effect. A
different level requires a new selection identity; reusing an identity with a
different level is a conflict.
