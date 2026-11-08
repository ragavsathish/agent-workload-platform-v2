# Slice 24: Owner client

## Goal

Recreate the current private owner workspace on top of the v2 conversation and
task capabilities. The owner can inspect and change conversations, send typed
or spoken turns, manage bounded work, and view safe results through the
browser and supported native shells.

## Boundary

The client presents existing session and task decisions; it does not own
conversation or task state. Every write uses the existing command boundary,
and every view reads the owner-scoped projection. Provider, media,
task execution, knowledge review, and deployment behavior stay in their
existing slices. The client may display a bounded interactive presentation but
does not grant it workspace or owner authority.

## Acceptance

- The workspace shows the owner's selected conversation, recent conversation
  catalog, safe transcript, and context usage.
- The workspace declares the commands and queries it uses; each displayed
  view is reached from an owner-scoped query and each write uses an explicit
  command path.
- The owner can start, resume, and switch conversations without losing stored
  history or changing a task identity.
- Typed and spoken turns use the existing accepted-turn and safe-observation
  paths. An interrupted turn remains visibly incomplete.
- The task view uses owner-scoped task and progress queries. It supports task
  creation, eligible cancellation, task attachment/detachment, and separate
  artifact retrieval.
- Task attachment uses the coordinated owner routing flow in [slice 25](../25-task-routing-integration/plan.md); it is not a client-only state change.
- Missing task artifacts produce a bounded retrieval error without changing
  the task's terminal outcome.
- The browser and native shell preserve the private workspace origin. Spoken
  interaction requests microphone access only on start and does not request
  camera access.
- Safe transcript and bounded presentations are visible; private reasoning,
  tools, credentials, and other owners' data are excluded.
- Reload and restart restore the server-selected conversation and task state;
  local presentation state never becomes authority.

## Evidence

The v1 library, tests, and package metadata are captured in
[the client source snapshot](../../raw/v1/client-source-capture.md). The
snapshot excludes generated build state and replaces the private host name
with a documentation-only placeholder.

## Documents

- [Domain rules](../../wiki/owner-client.md)
- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build tickets](tickets/)
