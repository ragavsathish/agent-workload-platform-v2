# Slice decisions and unresolved evidence

Cites the [plan](plan.md), [session identity decision](../03-session-orchestration/decisions.md#d08-explicit-command-identity-and-uncertainty), and [owner turn scenarios](spec.feature).

## D01: the client command owns the identity

The typed owner client generates a stable identity for one submission. The authenticated edge route passes that identity through the conversation service. Random server identities are insufficient for lost-response retry because a repeated HTTP request would become a second command.

## D02: unresolved command content is immutable

When the response is ambiguous, retain both the command identity and the submitted text. Retry must replay the identical payload. Do not let edited content reuse the unresolved identity; block it until the owner retries the pending submission or a later explicit recovery path resolves it.

## D03: no new private persistence dependency

The contract guarantees retry while the client instance retains the pending command. Process-reload recovery is deferred unless an existing secure, cross-platform persistence mechanism is available. Do not add an unreviewed local transcript store for this slice.

## D04: the session authority remains authoritative

The client does not infer whether a prior turn executed. Same-ID retries and conflicts are decided by the session authority; uncertain effects are not automatically dispatched under a new identity.
