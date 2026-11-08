---
type: reference
title: "Grain reverse engineering of AWP service topology"
---

# Grain reverse engineering of AWP service topology

This is a **reverse-engineered topology**, not a `defeventmodel` registration or
an event-sourcing migration. It uses [Grain's Event Model vocabulary](https://github.com/ObneyAI/grain/blob/dbf5b5229aad09785e40f71e96adf65c90f971d5/docs/event-model.md)
as an analysis lens: command, event, read model, query, todo processor, periodic
task, screen, and flow. The [bounded-context behavior specs](../spec/contexts/index.md)
remain the behavioral authority. Their `@current`, `@agreed`, and `@proposed`
labels are preserved here.

**Evidence rule:** “Observed” means a current code path or durable record was
inspected. “Candidate” names the corresponding Grain block if this behavior were
implemented in Grain. A past-tense EventStorming note is not evidence of a
published Grain event. A proposed block is a design obligation, not a deployed
service. The existing [EventStorming wall](one-big-wall.md) and
[aggregate map](phase-2-aggregates-contexts.md) supply domain boundaries; this
document supplies a service-topology inventory. Neither is a runtime catalog.

## Service-area inventory

Grain areas are service ownership boundaries, not necessarily processes. The
`C1`–`C9` names here retain AWP's existing bounded-context IDs; they are not
claims that a Grain registry already has nine areas.

| Area and status | Observed write or decision | Observed read / durable authority | Grain translation and unresolved edge |
| --- | --- | --- | --- |
| **C1 Voice Conversation — current** | A voice turn completes or is interrupted; a bounded work request can leave the conversation. | Session transcript and turn diagnostics. | Candidate `complete-turn` / `interrupt-turn` commands, turn facts, and conversation query. Confirm exact turn persistence and completion trigger before assigning event schemas. [Rule](../spec/contexts/c1-voice-conversation/spec.feature) |
| **C2 Tasking — current, with agreed retry work** | Gateway validates and submits agent, song, knowledge, and daily-account tasks. Pi Durable Owner deduplicates a request ID and owns native task lifecycle, abort, retry, and progress. | Pi Durable `Requests` document, native task checkpoint/result, bounded progress; Gateway composes owner views and artifact lookup. | Candidate admission and task commands, request/task projections, task queries, and execution processor. The Gateway currently generates a fresh ID for each direct agent submission, so the stable retry contract is only `@agreed`. [Rules](../spec/contexts/c2-tasking/spec.feature) |
| **C3 Personal Knowledge — current** | A listener submits newly observed completed turns for journal/classification; owner review accepts, corrects, or rejects knowledge. | Oxigraph review history and current knowledge interpretation; daily account references a source set. | Candidate review commands, review facts, knowledge projection, and owner query. Review history and interpretation are written by one SPARQL update, but replay and concurrent-review guarantees remain unverified. [Rules](../spec/contexts/c3-personal-knowledge/spec.feature) |
| **C4 Operational Incidents — current** | A poll observes managed alerts and submits a bounded investigation once per incident epoch. | Monitor SQLite incident row; Grafana supplies alert state; C2 owns the investigation task. | Candidate periodic poll, incident projection, and `request-investigation` command. An alert observation is an external input, not automatically a Grain event. [Rules](../spec/contexts/c4-operational-incidents/spec.feature) |
| **C5 Measurement and Qualification — proposed unified contract** | Independent tools can measure current artifacts, but there is no common qualification writer. | Allure, benchmark, and gate reports have separate owners. | Candidate `record-measurement` and `qualify-artifact` commands, immutable evidence facts, and qualification projection. Do not collapse measurement, verdict, and release authorization. [Rules](../spec/contexts/c5-measurement-qualification/spec.feature) |
| **C6 AWP Change Governance — proposed** | No current single change-case command owner. | Evidence and PR state are spread across tasks, measurements, and Git. | Candidate nomination and review-request commands plus change-case projection; external Git review remains in C9. [Rules](../spec/contexts/c6-awp-change-governance/spec.feature) |
| **C7 Component Regeneration — proposed** | Clean-room generation and single-candidate selection are specified, not deployed as one service. | No current run ledger or selection record. | Candidate cut, run, candidate, and selection commands; separate evidence and eligible-set projections; one versioned selection decision. [Rules](../spec/contexts/c7-component-regeneration/spec.feature) |
| **C8 Agent Execution — current** | Pi Durable invokes host or Gondolin execution under a selected workspace and capability grant. | Session/tool outcome returns to C2; image archive validation is a component boundary. | Candidate execution-start/completion facts only if C8 becomes a Grain writer. The task's terminal status still belongs to C2. [Rules](../spec/contexts/c8-agent-execution/spec.feature) |
| **C9 Repository Delivery — current external authority, proposed integration** | Git reviewer decides a PR; an operator deploys and verifies a revision. | Git host, checks, deployment reports, and observability. | Model Git and deployment as external effects or adapters until an AWP-owned delivery record exists. Do not synthesize a release event from an approved PR. [Rules](../spec/contexts/c9-repository-delivery/spec.feature) |

## Complete wall flow and policy trace

The area inventory alone does not describe ordering, alternate endings, or
feedback. This trace covers every decision on the [Phase 1 wall](expanded-storming.md)
and its [Phase 3 policies](phase-3-policies.md). The Grain column names the
decision's prospective command/query/processor seam, **not** a live Grain block.
“Current” includes documented manual actions; “proposed” means the integrated
decision path is not deployed.

| Wall decision | Grain-shaped seam and handoff | Successful and alternate endings | Policies / maturity |
| --- | --- | --- | --- |
| **D1 · Conversation** | C1 turn command or external inference result -> session view; a completed source turn may request C2 work and feed C3. | Answer completed **or** turn interrupted; a direct answer needs no task. | P4, P9 · current |
| **D2 · Admission** | C2 submit command reads owner, workspace, and prior request mapping before committing one task identity. | Accepted, identical retry returns the same task, or invalid/conflicting request refused. Unknown commit must be reconciled under the same ID. | P1 · current owner; stable Gateway retry agreed |
| **D3 · Execution and artifact** | C2 accepted task -> C8 host/Gondolin effect -> C2 task/result view; artifact lookup is a separate query/effect. | Completed, retained failure/abort, or explicit artifact lookup failure without rewriting completion. | P2–P3 · current |
| **D4 · Knowledge** | Listener sees a newly completed C1 turn -> independent C2 journal/classification submissions -> C3 suggestion query and owner review command. | Attributed suggestion accepted, corrected, rejected, or pending; either extraction may fail independently. First listener startup baselines older turns instead of enqueuing them. | P4–P5 · current; review replay/concurrency open |
| **D5 · Daily account** | An explicit request supplies C1 source messages -> C2 stable daily-build request -> C3 account writer/query. | Account references the exact owner/date/time-zone/source set, or the task fails; changed sources need a new request/version. A completed turn alone does not start a daily build. | P6 · current task; document version semantics open |
| **D6 · Personal song** | C2 lyric submission -> lyric result/manifest query -> owner review -> separate render submission -> audio artifact query. | Lyrics prepared, then reviewed render succeeds; invalid/incomplete lyrics, cancellation, render refusal/failure, or missing audio are distinct endings. Approval is currently a manual fact. | P7 · current with manual approval |
| **D7 · Alert investigation** | C4 periodic poll and incident query -> one stable C2 request -> C2 finding query -> owner review. | Finding, retained investigation failure, or unjudged alert; duplicate poll stays within one epoch. | P8–P9 · current |
| **D8 · Owner next action** | Owner reviews a source-specific C1/C2/C3/C4 outcome; proposed C6/C7 nomination commands would capture provenance. | Close, follow up, nominate an AWP change, or nominate a component. An alert alone authorizes no code change. | P9 · human review current; governed nomination proposed |
| **D9 · AWP qualification** | C2 change artifact and exact diff -> C5 evidence/qualification query -> C6 change-case decision. | Qualified only with complete trusted matching evidence; otherwise held or unjudged, with no PR request. | P10 · proposed integrated gate |
| **D10 · PR soundtrack** | C9 exact PR diff -> C2 lyric/render work -> owner approval -> pinned audio -> C9 PR update. | Soundtrack attached or held. Whether an authorized exception permits review is open; missing audio cannot silently count as attached. | P11–P13 · manual song/PR path current; automated link proposed |
| **D11 · AWP PR and deployment** | C9 reviewer decides the exact PR revision; operator deploys and verifies that revision. | Changes requested or approved; then verification passes, fails, or requires recovery/rollback. Approval is a separate fact from release verification. | P14 · manual current; unified gate proposed |
| **D12 · Component cut** | C7 owner nomination/ratification reads target repo, base, boundary, interface, authority, and gate. | Boundary/cut held, or immutable cut digest ratified; later learning makes a new cut. | P15 · proposed |
| **D13 · Fence and candidates** | C7 fence command proves allowed inputs before candidate generation; isolated generation effects retain run and candidate IDs. | Fence failure holds the run before generation; successful fence permits parallel candidate success/failure without sibling overwrite. | P16–P17 · proposed |
| **D14 · Measure and select** | C5 independently records each candidate's evidence; C7 eligible-set query informs a separate versioned selection command. | Candidate held/unjudged or eligible; one authorized digest selected, or stale/competing/no-eligible selection refused. Eligibility alone never promotes. | P18–P19 · proposed |
| **D15 · Target PR and learning** | C7 selected digest -> C9 target repository PR/release; AWP target joins D10–D11; reviewed finding -> new C7 cut proposal. | PR changes requested, release verified/held/failed, or learning recorded for a later cut. Prior cut and evidence remain attributable. | P20–P21 · proposed join; target Git authority current |

The complete paths are therefore:

```text
conversation:  D1 -> {D2 -> D3, D4, D5} -> D8
song:          D6 -> D8
alert work:    D7 -> D8
AWP change:    D8 -> D9 -> D10 -> D11 -> D8 (later observation/review)
replacement:   D8 -> D12 -> D13 -> D14 -> D15 -> D12 (later learning)
AWP candidate: D14 -> D15 -> D10 -> D11
```

The braces show independent branches, not a required serial order. Refusal,
failure, and held states are terminal for that attempt until a new authorized
command, retry, or versioned decision occurs. A proposed Grain processor could
react at a seam above only after its triggering fact, query, effect checkpoint,
idempotency rule, and owner authority are specified; the policy cards alone do
not establish a production event subscription.

## Source audit of current paths

The current labels above were checked against these implementation seams. This
audit does **not** turn the manual/proposed D8–D15 path into source-backed code.

| Claim checked | Source observation | Consequence for the model |
| --- | --- | --- |
| New turn -> knowledge work | The [session listener](../../products/voice-agent/apps/pi-agent/digital-twin/runtime/session-listener.ts) scans Pi session branches, sends newly completed owner turns to Gateway, and persists a seen set. On first startup without a seen file, it baselines existing turns. | Model a scanner/adapter and stable source identity, not an assumed event subscription or automatic backfill. |
| Task submission and retry | [Gateway TaskService](../../products/voice-agent/packages/tasking/src/voice_agent/tasking/application/service.py) generates a fresh UUID for ordinary agent work; [Owner.submit](../../platform/agent-execution/pi-durable-task/owner.ts) atomically maps a request ID to one native task and compares the serialized input on retry. | Owner deduplication is current; caller-stable ordinary retry is agreed work. Exact payload equality is narrower than a still-undefined canonical-intent contract. |
| Task status, progress, and artifact | [Owner.get/progress](../../platform/agent-execution/pi-durable-task/owner.ts) read native task snapshots and bounded progress; [TaskService.artifact](../../products/voice-agent/packages/tasking/src/voice_agent/tasking/application/service.py) checks owner identity, expected artifact name/URI, and object availability separately. | Native completion and artifact retrieval must remain separate facts. Neither is a Grain projection today. |
| Daily account | [TaskService.enqueue_daily_journal](../../products/voice-agent/packages/tasking/src/voice_agent/tasking/application/service.py) requires an explicit source bundle and derives a stable request ID from owner, date, time zone, and source hash. | A turn completing does not itself invoke the daily account command. |
| Song | [SongService](../../products/voice-agent/packages/tasking/src/voice_agent/tasking/application/song_service.py) gives lyrics and render separate deterministic request IDs and checks `decide_render` before starting render. | The code proves manifest/readiness gating; it does not record a durable owner approval decision. |
| Knowledge review | Gateway builds one [SPARQL review update](../../products/voice-agent/apps/gateway/src/voice_gateway/main.py) that changes current status and adds an attributed review record. | The write shape is source-backed; Grain-style replay, version guards, and concurrent-review behavior have not been established. |
| Alert investigation | [Host monitor](../../products/voice-agent/tools/ops/stack_alert_monitor.py) stores incident keys and submission state in SQLite, derives a stable request ID from rule/fingerprint/epoch, and submits to the owner. | This is a periodic external poll plus an idempotent task request, not a current Grain periodic task or event stream. |
| Execution mode | [Session execution](../../platform/agent-execution/pi-durable-task/session-execution.ts) selects `host` or `gondolin` and constrains the workspace path passed to Pi. | C8 is a runtime effect boundary; C2 still owns the public task outcome. |

The code audit finds **no implemented C5–C7 change-case, qualification, or
regeneration service**, and no automatic C9 soundtrack/PR/release coordinator.
Those decisions remain in specs, reports, and manual Git/operator workflows.

## C2: observed topology and Grain translation

C2 is the most concrete candidate for a first Grain area. Its current path is:

```text
owner client / Gateway
  -> TaskService.submit validates instruction and workspace
  -> Pi Durable Owner.submit commits request ID + native task creation
  -> native task executes, checkpoints, and records bounded progress/result
  -> Owner.get / progress / describe expose state
  -> Gateway checks ownership and projects an owner view
  -> artifact lookup reads the referenced object separately
```

This is supported by [Gateway TaskService](../../products/voice-agent/packages/tasking/src/voice_agent/tasking/application/service.py)
and [Pi Durable Owner](../../platform/agent-execution/pi-durable-task/owner.ts).
The owner transaction rejects a reused request ID with different serialized input
and reuses the existing native task for the same input. The Gateway's ordinary
`submit` path currently creates a new UUID before calling that owner; the
[stable retry rule](../spec/contexts/c2-tasking/spec.feature) is therefore
`@agreed`, not a verified end-to-end guarantee. Knowledge and daily-account
submissions already derive stable IDs from their source identities.

| Grain block | Candidate name | Current evidence / gap |
| --- | --- | --- |
| Command | `:tasking/submit-task` | Gateway `TaskService.submit`; owner `submit` is the authoritative write. A future command must authorize owner/workspace and preserve a caller-stable request ID. |
| Command | `:tasking/abort-task`, `:tasking/retry-task` | Owner control methods. Exact retry semantics need separate trace evidence before an event contract is fixed. |
| Event | `:tasking/task-accepted`, `:tasking/task-completed`, `:tasking/task-failed` | **Candidate facts only.** Current Pi Durable checkpoints/results are not registered Grain events. A refusal produces no acceptance fact. |
| Event | `:tasking/public-progress-recorded` | **Candidate fact only.** Current bounded progress lives in the owner request document. |
| Read model | `:tasking/requests`, `:tasking/tasks`, `:tasking/public-progress` | Current equivalents are the owner request document and native task snapshots. Define deterministic reducers and replay/retention rules before claiming Grain projections. |
| Query | `:tasking/task`, `:tasking/task-list`, `:tasking/task-progress` | Owner `get`, `listNative`, and `progress`, with Gateway ownership checks. An adapter must remain default-deny for another owner's data. |
| Todo processor | `:tasking/execute-accepted-task` | Native Pi Durable scheduler currently performs execution. A Grain processor would need an explicit checkpoint/effect choice and idempotent external work. |
| Screen | `:tasking/task-board` | Browser task views consume Gateway APIs. A Grain screen declaration would need a corresponding Allium surface. |

A **target Grain flow**, if C2 were migrated, would be `screen -> submit
command -> task-accepted event -> task read model -> task query -> screen`.
Execution would continue through `query -> todo processor -> execution command`;
the external host/Gondolin effect and artifact store remain explicit boundaries.
This is a target translation, not the runtime path above. It follows Grain's
[legal flow adjacency](https://github.com/ObneyAI/grain/blob/dbf5b5229aad09785e40f71e96adf65c90f971d5/docs/event-model.md#flows-and-edges).

## Cross-area joins that must remain explicit

| Handoff | Identity passed | Writer / source of truth | Required check |
| --- | --- | --- | --- |
| C1 -> C2 bounded work | Source turn, owner, workspace, stable request identity | C2 decides admission; C1 keeps the source turn. | Retry with the same canonical request cannot create another task. |
| C1 -> C3 knowledge | Session/message source identity | C3 review history; C2 separately owns extraction tasks. | Review cannot silently accept an unattributed suggestion. |
| C4 -> C2 investigation | Managed rule, fingerprint, epoch, derived request ID | C4 incident row; C2 task identity/outcome. | Duplicate polling retains one investigation for the epoch. |
| C2 -> C8 execution | Task ID, execution mode, workspace grant | C2 task outcome; C8 session/workspace effect. | Execution cannot decide admission or access another grant. |
| C5 -> C6/C7 qualification | Artifact digest, obligation/oracle version, evidence IDs | C5 verdict; C6/C7 make later decisions. | Missing evidence is unjudged/failed, never an implicit pass. |
| C7 -> C9 selected candidate | Repository, component, base revision, selected digest | C7 selection; target Git review in C9. | A stale or competing selection is refused; release is a later decision. |

These handoffs are correlations across owners. They are not cross-area atomic
transactions. The [parallel-source simulation](parallel-source-simulation.md)
tests a proposed convergent read view, but does not prove live delivery,
recovery, or projection replay.

## What is required for an executable Grain Event Model

1. Distill the observable rules into Allium declarations. Grain requires every
   command to link to an Allium rule and every screen to an Allium surface;
   Gherkin IDs alone do not satisfy that composition gate. Preserve the current
   stable IDs as trace metadata.
2. For a selected area, inspect live `defcommand`, `defevent`, `defreadmodel`,
   `defquery`, processor, and screen registrations. There are no such Grain
   registrations in the current AWP paths reviewed here. Do not register the
   candidate events above as if the existing SQLite/Oxigraph records were Grain
   events.
3. Define Malli payload schemas, `:reads`/`:produces`/`:consumes` edges,
   authorization, tenant identity, and event history policy from the actual
   implementation contract. Then write `defeventmodel` and link the Allium
   declarations.
4. Run Allium checks, Grain composition validation, and Grain's strict runtime
   Event Model validator against the loaded application. The existing
   [context checker](../spec/contexts/check.mjs) checks spec references and
   syntax only; it cannot supply these runtime verdicts.

The first unresolved design decision is whether AWP is **adopting Grain** for
C2 or only using its vocabulary to analyze the existing system. The former
requires a separate, reviewed persistence and migration decision; the latter
can use this inventory without changing the runtime.
