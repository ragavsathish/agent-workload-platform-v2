# Personal knowledge from owner turns

Cites the [C3 contract](../../raw/v1/c3-personal-knowledge.feature), [decision wall](../../raw/v1/expanded-storming.md), [policy wall](../../raw/v1/phase-3-policies.md), [completed-turn listener](../../raw/v1/session-listener.ts), [task service](../../raw/v1/task-service.py), [knowledge routes](../../raw/v1/gateway-main.py), and [source regression](../../raw/v1/personal-knowledge-regression.py).

## Boundaries and terms

A **source turn** is a completed owner message with a stable conversation and message identity. The v1 listener recognizes a nonempty owner message only after a following assistant message; it excludes developer-origin messages. On its first start without a seen record, it marks older completed turns seen instead of ingesting them. Later scans submit unseen turns and record them seen after a successful submission. A pending or interrupted message is not a completed source turn. This describes the current listener, not a promise of historical backfill. See the [listener](../../raw/v1/session-listener.ts).

For each admitted source turn, **journal extraction** and **classification extraction** are separate durable tasks. Their identities include the source and mode. One may complete or fail without changing the conversation answer or the other task. A submitted extraction is not itself accepted personal knowledge. See the [task service](../../raw/v1/task-service.py) and [policy wall](../../raw/v1/phase-3-policies.md).

A **suggestion** is an attributed journal entry or candidate assertion with its source. A **review action** is the owner's keep, edit, type correction, delete, accept, or reject decision, depending on suggestion kind. The v1 routes record review facts and update the current interpretation; accepted candidate assertions become profile facts. A suggestion remains reviewable before that action. Review history and source attribution must remain inspectable after a later correction. See the [knowledge routes](../../raw/v1/gateway-main.py) and [C3 contract](../../raw/v1/c3-personal-knowledge.feature).

## Commands, outcomes, and reads

| Command or trigger | Required facts | Observable outcome |
| --- | --- | --- |
| Observe completed owner turn | Owner, conversation and message identity, completed transcript and capture time | Two independently trackable extraction submissions, or an inspectable intake failure. Duplicate observation does not create new work. |
| Record extraction result | Source identity, task identity, attributable output | Reviewable suggestions for that source; empty or failed extraction remains visible as its own outcome. |
| Review journal entry | Owner authority, target entry and current revision, chosen action and valid correction fields | Append-only action and a corresponding current entry interpretation, or a stale/missing-target refusal. |
| Review candidate assertion | Owner authority, target candidate and current revision, accept or reject | Append-only action and corresponding current assertion interpretation; only acceptance may create an accepted profile fact. |
| Request daily account | Owner, local date, time zone, explicit bounded owner-message source bundle | One durable build identity for that exact bundle, or a validation refusal. |
| Record daily account | Matching request and exact source bundle | Account attributed to owner, date, time zone, and sources, or a retained task failure. |

The owner can read source-backed suggestions, review history, current interpretation, and the daily account. Reads are scoped to the authenticated owner. Unreviewed assertions cannot appear as accepted profile facts. Rejected or deleted suggestions do not disappear from their attributed review history. The current v1 route uses a single write for a review action and interpretation change, but it does not prove retry idempotency or a competing-review guard; those are [slice 07 decisions](../plan/07-personal-knowledge/decisions.md). See the [knowledge routes](../../raw/v1/gateway-main.py).

## Daily account

A completed turn does not automatically request a daily account. A separate request supplies at least one owner message, at most 300 sources, unique source references, a local date, and a time zone. Each source's capture time must fall on that local date; total transcript text is bounded. The request identity includes owner, date, time zone, and exact source bundle, so a changed bundle is another request. The current writer uses a date-and-zone page identity and replaces the page contents on write, leaving document version semantics open. Slice 07 requires each version to retain its exact source identity and to remain distinguishable. See the [task service](../../raw/v1/task-service.py), [knowledge routes](../../raw/v1/gateway-main.py), and [C3 contract](../../raw/v1/c3-personal-knowledge.feature).

## Privacy and authority

Source transcripts and extracted claims are personal data. Owner-facing queries and review commands require authenticated owner scope; an inner writer credential alone grants no owner review authority. Source text and private claims stay out of general task progress and public conversation projections. A source reference is evidence for an item, never a capability to read another owner's item. See the [knowledge routes](../../raw/v1/gateway-main.py), [C3 contract](../../raw/v1/c3-personal-knowledge.feature), and [owner isolation rules](tasking.md).
