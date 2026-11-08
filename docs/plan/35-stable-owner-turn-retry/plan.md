# Slice 35: stable owner typed-turn retry

Cites the [session command identity decision](../03-session-orchestration/decisions.md#d08-explicit-command-identity-and-uncertainty), the [owner client contract](../24-owner-client/spec.feature), the [typed turn route](../../wiki/session-orchestration.md), and the [pinned v1 source inventory](../../../raw/v1/manifest.md).

## Goal

Make a typed owner submission keep one command identity while its outcome is ambiguous, so retrying it converges on the existing owner turn rather than dispatching a second external effect.

## Boundary

The slice begins when the owner submits typed message text and ends when the matching turn returns a response or remains visibly retryable. The client retains the pending identity and exact submitted text after a transport or server error. Retrying the same text sends the same identity. A different text cannot replace an unresolved submission. After success, the next submission receives a fresh identity, including when its text is identical. The session authority decides reused identity and payload conflicts.

The durable contract requires retry during the current client lifetime. Process-reload recovery is not added unless an existing owner-safe persistence mechanism can store the pending command without creating an unbounded local transcript or compromising privacy.

## Acceptance

- A typed turn request carries a fresh idempotency key through the owner route; the edge propagates it to the conversation service.
- An ambiguous failure retains the exact message and identity; retry uses both unchanged.
- A successful turn clears the pending command; a later new submission receives a different identity.
- A different message cannot reuse an unresolved identity or silently replace its pending command.
- A same-ID/different-input conflict is surfaced as a bounded owner error and never causes a new turn dispatch.

## Verification

Record a failing owner-client acceptance scenario before implementation. Verify captured request identities for first submission, ambiguous retry, a new post-success submission, and a changed-payload refusal. Exercise the real conversation path with a same-ID retry and conflicting reuse where the available local harness permits. Run owner-client acceptance, documentation, formal specification, local link, build, and whitespace checks.

## Documents

- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-stable-turn-identity.md)
