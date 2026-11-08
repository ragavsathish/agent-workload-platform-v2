# Decisions

Cites the [Slice 29 plan](../29-session-compaction-reconciliation/plan.md),
[session rules](../../wiki/session-orchestration.md), and
[root build contract](../../../BUILD-CONTRACT.md).

## D115 — Active-turn compaction queues by command identity

Slice 29 behavior already present in the runtime and formal session contract retains an
eligible compaction request while the owner turn runs, then promotes it after
settlement. Durable plan language will describe that behavior, including
revalidation before the Pi effect. The same command identity survives the
queue; the request does not start compaction concurrently with the turn.

## D116 — Root build contract survives the implementation fence

Architecture choices and minimum runtime ownership/bootstrap constraints belong in a
root-level durable document. Slice tickets cite pinned raw evidence for source
facts. The formal session contract remains authoritative for session semantics.

## D117 — No invented public compaction wire schema

This correction documents durable architecture and queue semantics without
inventing request or response fields for a client protocol that the pinned
source does not define. Client persistence details remain explicitly bounded
by the existing owner-client contract until a separate decision resolves them.

## D118 — Stale queued compaction is rejected without an external effect

After the turn settles, the command rechecks current selected conversation,
attachment state, and refreshed context eligibility. If any check fails, the
queued command reaches a rejected terminal state, releases its session lock,
and never calls Pi. A later deliberate attempt uses a new command identity.
