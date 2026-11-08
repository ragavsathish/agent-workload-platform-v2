# Slice 36: recover typed-turn identity after owner-client reload

Cites the [stable typed-turn retry slice](../35-stable-owner-turn-retry/plan.md), [session identity decision](../03-session-orchestration/decisions.md#d08-explicit-command-identity-and-uncertainty), and [session orchestration rules](../../wiki/session-orchestration.md).

## Goal

Let the owner workspace recover the identity and outcome of its most recent typed turn after a page reload, without storing prompt text in browser storage or replaying a model effect.

## Boundary

The durable session record remains authoritative for accepted turn identity, status, and answer. An authenticated owner query returns a minimal receipt for the newest typed turn in the selected conversation, excluding attached-task turns. The receipt contains no prompt, fingerprint, tool data, or provider detail. The owner client correlates that identity with its already owner-visible transcript entry, restores the same ID and exact text as its pending submission when safe to retry, and refreshes the durable transcript to recover completed answers. Accepted and uncertain turns remain visibly unresolved and cannot be replaced or automatically redispatched.

## Acceptance

- The latest typed-turn receipt is owner-scoped and limited to the active selected conversation.
- The receipt exposes only stable identity and bounded status; it does not expose prompts, fingerprints, tool payloads, or another owner's turns.
- After a reload with a completed typed turn, the workspace rebuilds the answer from the durable transcript and does not submit a second turn.
- After a reload with an unresolved accepted or uncertain turn, the workspace restores the existing turn identity and message from the owner-visible transcript, blocks changed content, and never claims another model dispatch.
- A later new typed submission after a completed turn receives a fresh identity, even when its text is identical.
- An attached-task turn is not misidentified as the selected conversation's typed turn receipt.

## Verification

Record a named red owner-client/reload scenario before implementation. Exercise session projection ownership and query filtering, restart recovery of an accepted turn to uncertain, same-ID retry with no second model call, workspace startup and message submission, and the authenticated owner boundary. Run the relevant session, live protocol, edge/broker, owner-client, formal contract, documentation, link, build, and whitespace gates.

## Documents

- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-reload-recovery.md)
