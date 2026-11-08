# Slice 23: Deployment levels

## Goal

Let an operator select `voice`, `durable`, or `full` and bring the matching
capabilities into service while preserving owner, session, task, review, and
artifact identity. If no level is configured, select `full`.

## Boundary

This slice governs capability selection and the operator's view of requested
level, active services, and readiness. It does not change public route gates,
owner authority, voice privacy, task workspace approval, or the meaning of
existing task recovery. Public route behavior remains specified by slice 22.

## Acceptance

- Voice conversations remain available at every level; the persistent voice
  tool environment remains isolated.
- A level that cannot execute durable tasks refuses a new task before
  acceptance.
- Durable tasks at `durable` use the host task executor; tasks at `full` use
  the isolated task executor.
- A transition starts and stops the capabilities required by the requested
  level. A service failure is visible as not ready or degraded.
- A transition preserves stored identities and decisions. An interrupted task
  remains the same task and is not automatically replayed.
- Invalid levels leave the active configuration unchanged. Repeating the
  active level is idempotent.
- The selected level survives process restart, and readiness is reconstructed
  from observed service health.

## Evidence

The source behavior is captured in [the v1 deployment-level worktree
snapshot](../../raw/v1/deployment-levels-worktree.md). `voice`, `durable`, and
`full` were documented in a v1 working tree, not a released commit. Slice 22
provides the public deployment and route boundary.

## Documents

- [Domain rules](../../wiki/deployment-levels.md)
- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build tickets](tickets/)
