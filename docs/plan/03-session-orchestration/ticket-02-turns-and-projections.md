# Ticket 02: turns and projections

Cites [slice plan](plan.md), [conversation rules](../../wiki/session-orchestration.md),
and scenarios [session-007 through session-011 and session-017](spec.feature).

Admit voice and typed turns to the same active identity. Serialize turn and
maintenance decisions, record completion, failure, and interruption without
changing selection, and project bounded public transcript and context usage.
Verify the public view omits private reasoning and tool payloads, and a
delayed answer cannot be attributed to a later selected conversation.
An identical turn retry returns its recorded decision without dispatching
again, including when its outcome is uncertain; conflicting identity reuse
refuses.
