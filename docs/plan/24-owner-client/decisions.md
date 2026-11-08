# Decisions

Cites the [captured client source](../../raw/v1/client-source-capture.md), the
[session rules](../../wiki/session-orchestration.md), and the [task
rules](../../wiki/tasking.md).

## D105 — The client presents server-owned decisions

Conversation selection, accepted turns, task state, progress, and result
availability come from the server. Client-local state may preserve presentation
preferences but cannot become the authority for those decisions.

## D106 — Client reads remain owner-scoped

Every history, transcript, task, progress, and artifact view is checked against
the authenticated owner. A missing or foreign identity receives no content.

## D107 — Turn and task outcomes remain separate

The client presents task creation and task completion separately from the
conversation that requested it. Artifact retrieval is separate from terminal
task status.

## D108 — Embedded presentations receive bounded input

A supported interactive presentation receives only its declared presentation
content. It cannot access host tools, credentials, arbitrary network origins,
or workspace commands.

## D109 — Voice permission follows explicit owner action

Request microphone permission when the owner starts spoken interaction. Do not
request camera access. The native shell remains within its configured private
workspace origin.
