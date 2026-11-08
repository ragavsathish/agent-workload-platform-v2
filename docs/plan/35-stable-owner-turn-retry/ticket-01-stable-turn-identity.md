# Ticket 01: carry a stable typed-turn identity

Generate the owner typed-turn identity at the client command boundary and pass it as an idempotency key through the authenticated edge route to the conversation service. Retain the pending identity and exact input after an ambiguous response so a user retry repeats the same command. Clear it on success; issue a new identity for a later command. Refuse changed content while an earlier command remains unresolved. Preserve bounded error behavior for identity conflicts. Acceptance: `owner-turn-001` through `owner-turn-004`.

See the [session command identity decision](../03-session-orchestration/decisions.md#d08-explicit-command-identity-and-uncertainty) and this slice's [acceptance scenarios](spec.feature).
