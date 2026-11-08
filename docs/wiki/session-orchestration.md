# Current conversation orchestration

Cites the [pinned source inventory](../../raw/v1/manifest.md), especially the
[orchestrator](../../raw/v1/voice-agent.ts), [session catalog](../../raw/v1/voice-sessions.ts),
[public transcript](../../raw/v1/public-transcript.ts), [turn parser](../../raw/v1/conversation.ts),
[serialized operations](../../raw/v1/serial-executor.ts), [owner routes](../../raw/v1/edge-bot.py),
and [source tests](../../raw/v1/voice-service.test.mjs).

## Terms and authority

An **orchestrator conversation** is the persistent sequence of owner turns selected
for the voice agent. Its **conversation identity** selects one saved history. The
**active selection** identifies which conversation receives the next unattached
turn. A historical conversation can be read without becoming active. On service
restart, v1 opens the most recent saved conversation; it does not prove that a
selection of an older saved conversation survives restart. A fresh selection
creates another identity while retaining the old history. See the
[orchestrator](../../raw/v1/voice-agent.ts),
[session catalog](../../raw/v1/voice-sessions.ts), and
[source tests](../../raw/v1/voice-service.test.mjs).

A **voice connection** is a browser interaction lifetime; it is not the
conversation identity. Voice and typed owner turns reach the same active
orchestrator conversation. The long-lived **tool environment** belongs to the
orchestrator service and remains available across conversation changes. Changing
the selected conversation does not grant or rotate its capabilities. See the
[product description](../../raw/v1/voice-product-readme.md) and
[orchestrator](../../raw/v1/voice-agent.ts).

A **durable task attachment** temporarily routes subsequent turns to an
independently owned task conversation. It is neither the orchestrator's active
conversation nor the tool environment. Switching the selected orchestrator
conversation releases the attachment; ending the voice connection also releases
it. The task's own identity and history remain with the task. See the
[orchestrator](../../raw/v1/voice-agent.ts),
[attachment source](../../raw/v1/remote-task-session.ts), and
[product description](../../raw/v1/voice-product-readme.md).

## Commands and resulting facts

| Command | Conditions | Observable fact |
| --- | --- | --- |
| Start fresh conversation | Authenticated owner; no turn or maintenance in progress | A new identity becomes active, live transcript resets, previous history remains readable, task attachment releases. |
| Resume saved conversation | Authenticated owner; no turn or maintenance in progress; identity exists | Selected identity and live transcript change to that saved conversation; task attachment releases. |
| Submit owner turn | Authenticated owner; valid final user or bootstrap instruction; bounded input | One turn is addressed to the active conversation, or to the attached task; public user and assistant observations become visible. |
| Stop active turn | Authenticated owner | Active conversation or attached task receives an interruption request; the selected identity remains. |
| Compact context | Authenticated owner; serialized with turns | Retained context changes; its usage projection refreshes. |
| Attach or detach task | Eligible task identity and owner control | Following turns route to that task, or return to the selected orchestrator conversation. |

V2 records a context-compaction command identity, selected conversation, and Pi
branch leaf in owner event store before invoking Pi. owner event store marks completion only after the
Pi session contains a new compaction entry parented to that leaf. If the branch
does not prove the effect, the command remains pending/uncertain and is not
invoked again automatically. Pending compaction blocks turns, selection, and
task routing so the branch cursor cannot move underneath reconciliation. The
owner-scoped context read exposes the pending command identity/status, allowing
the owner client to resume it after a lost response or page reload. See
[slice 29](../plan/29-session-compaction-reconciliation/plan.md).

The source serializes turns and maintenance. It refuses conversation switching
while either is pending. A turn timeout requests interruption and reports an
error. A streamed turn can be interrupted when the connection closes. The
source does not prove that an interrupted model turn has no side effects. See the
[orchestrator](../../raw/v1/voice-agent.ts),
[serialized operations](../../raw/v1/serial-executor.ts), and
[service route](../../raw/v1/voice-http-server.ts).

## Read models and queries

| Read | Source-backed visibility rule |
| --- | --- |
| Active transcript | A bounded display-safe projection of the selected conversation, updated during a turn and restored from saved history after restart or resume. |
| Historical transcript | A saved conversation can be read by identity without selecting it; missing identity returns a bounded missing-session answer. |
| Conversation catalog | At most 30 recent conversations, active selection first, with title, update time, message count, and active marker; no host path. |
| Context usage | Current or restored token/window estimates and compaction eligibility; no transcript content. |

The public transcript exposes user and assistant text plus sanitized tool names
and status. It excludes private reasoning, tool inputs, and tool results. The
live projection retains at most 200 entries and truncates displayed text to
24,000 characters; the historical projection is likewise capped. A revision
marks projection changes. See the
[public transcript](../../raw/v1/public-transcript.ts),
[session catalog](../../raw/v1/voice-sessions.ts), and
[client reads](../../raw/v1/client-api.dart).

The existing owner-facing contract names are `POST /api/pi-sessions/new`,
`POST /api/pi-sessions/{id}/resume`, `GET /api/pi-sessions`,
`GET /api/pi-sessions/{id}`, and `GET /api/pi-transcript`. Fresh and resume
return `{id}`; catalog returns `{sessions}` with each item's `id`, `title`,
`updatedAt`, `messageCount`, and `active`; both transcript reads return
`{revision, entries}`. Missing history or resume returns a bounded missing
answer, and a busy selection returns a conflict answer. The typed turn enters
through `POST /api/pi-message` with one bounded `message`, while voice and the
inner turn route send the same latest actionable prompt to the selected
conversation. See the [owner routes](../../raw/v1/edge-bot.py),
[service route](../../raw/v1/voice-http-server.ts), and
[client reads](../../raw/v1/client-api.dart).

An attached task turn is visible in the live public transcript. That task's
conversation owns its durable history; the orchestrator's historical read
does not acquire the task turn merely because it was attached. See the
[orchestrator](../../raw/v1/voice-agent.ts) and
[attachment source](../../raw/v1/remote-task-session.ts).

All owner-facing reads and controls require authenticated owner identity.
The inner service requires a separate service credential. Invalid session
identities are rejected before history lookup; foreign callers cannot list,
read, or select a conversation. See the
[owner routes](../../raw/v1/edge-bot.py),
[broker routes](../../raw/v1/broker-http.py), and
[service route](../../raw/v1/voice-http-server.ts).

## Source gaps carried into planning

The v1 source has no caller-supplied command identity for fresh selection,
resume, or a turn. It does not define replay behavior after a lost response.
It also does not persist a resumed older selection as a separate selection fact.
The catalog is derived from saved conversations, while the active transcript
is also maintained live; the source does not provide one durable event stream
that makes both projections atomic. Task attachment is process-local and does
not prove restoration after restart. These gaps are resolved or deferred in
[slice 03 decisions](../plan/03-session-orchestration/decisions.md).
