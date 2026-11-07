# Slice 07 decisions

Cites [personal knowledge rules](../../wiki/personal-knowledge.md), [source listener](../../../raw/v1/session-listener.ts), [source task service](../../../raw/v1/task-service.py), [source routes](../../../raw/v1/gateway-main.py), and the [policy wall](../../../raw/v1/phase-3-policies.md).

## D25: intake follows a settled source turn

Use the current conversation's settled owner turn as the source boundary. Its stable owner, conversation, and message identity names one intake. Ignore incomplete, interrupted, empty, and non-owner turns. Duplicate notification reconciles the same intake and task identities. Historical turns require an explicit future import decision; starting a new listener does not silently backfill them. The v1 listener baselines old turns and records later ones after submission; the v2 boundary makes the distinction visible.

## D26: two tasks, two outcomes

Journal and classification extraction are separate accepted work keyed by source and mode. Persist each submission and outcome independently. A failed or unknown submission remains inspectable and retryable under its same identity; it never converts the already settled conversation answer into failure. No extraction output becomes accepted knowledge by task completion alone.

## D27: review is a versioned owner decision

For a target suggestion, record an immutable owner action and update its current interpretation as one logical decision. Require the owner and an expected target revision. An identical repeated action returns the existing decision; a conflicting or stale action is refused with the current revision. Corrections preserve the original suggestion and prior reviews. Only a reviewed accepted assertion appears as an accepted profile fact. The v1 update shape records history and current interpretation together, but its concurrent-review and retry guarantees are unverified.

## D28: source-backed, scoped reads

Every suggestion and current claim exposes its originating source identity and extraction outcome. Owner queries show pending, accepted, corrected, rejected, and failed states without leaking private text through general task or conversation summaries. Resolve owner authority before source or item lookup. A writer service identity cannot act as the owner.

## D29: daily account versions name exact sources

Build only on an explicit owner request with a bounded, same-local-date source bundle. Identity includes owner, date, time zone, and a canonical source-set digest. The same request returns the same task/account version; changed source content or membership yields a new version. Preserve prior version attribution and make any current-version selection explicit. The v1 request identity includes source content, while its date-and-zone page replacement leaves history semantics open.

## Open evidence

The pinned v1 sources do not prove deterministic extraction quality, a transactional link across task result and knowledge write, replay-safe review event identity, or a concurrency guard. The build must test these v2 decisions; live model quality and migration of an existing private knowledge store require separate qualification and owner data policy.
