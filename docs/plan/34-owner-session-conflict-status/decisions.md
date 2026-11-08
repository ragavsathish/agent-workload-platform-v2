# Decisions

Cites the [Slice 34 plan](plan.md), the
[live conversation rules](../../wiki/live-session-integration.md), and the
[compaction hold](../33-compaction-uncertainty/plan.md).

## D125 — Use typed command failure, not message matching

The session service returns a stable HTTP status and bounded error code for
refused commands. The Pi client preserves those fields as a typed error. The owner route
maps only that typed conflict to HTTP or stream status 409. Arbitrary Pi,
provider, timeout, and parsing errors remain 502; their messages are not
searched for words such as `busy` or `conflict`.

## D126 — Keep refusal details bounded

The owner receives a generic session-conflict detail. Internal service error text,
owner identifiers, provider payloads, and private session state are not copied
into the public error. The authenticated owner can query the owner context for
the safe state needed to decide what to do next.
