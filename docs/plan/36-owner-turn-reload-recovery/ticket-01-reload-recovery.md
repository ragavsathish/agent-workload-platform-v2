# Ticket 01: owner typed-turn reload recovery

# Ticket 01: owner typed-turn reload recovery

Cites the [slice plan](plan.md), [decisions](decisions.md), and the [session command identity decision](../03-session-orchestration/decisions.md#d08-explicit-command-identity-and-uncertainty).

Add an owner-scoped query for the newest typed turn in the selected conversation. The query must authenticate the owner through the existing session authority and return only the turn ID and bounded status (plus other strictly necessary non-content recovery fields). Exclude task-routed turns. Expose the query through the authenticated owner path. On workspace startup, reconcile that receipt with the safe transcript. A completed turn is restored from the transcript; an accepted or uncertain turn retains its existing ID and exact transcript text, blocks edits/new submissions, and can only be inspected or retried with that same identity and payload. Never redispatch a same-ID turn whose receipt is already accepted/uncertain. Acceptance scenarios: `owner-turn-recovery-001` through `owner-turn-recovery-004`.
