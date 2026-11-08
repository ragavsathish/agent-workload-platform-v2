# Prove the session bootstrap and projection rebuild

Use the [Slice 31 plan](plan.md), [acceptance scenario](spec.feature),
[session contract](../../wiki/session-orchestration.md),
[root build contract](../../../BUILD-CONTRACT.md), and
[Slice 30 regeneration report](../30-session-compaction-regeneration/regeneration-delta.txt).

Exercise the existing session system API against its durable event store. Verify
that opening validates the declared session contracts, normal command/query
paths persist and read facts, and close/reopen reconstructs the owner
projection. Include selection, transcript, turn, context, routing recovery, and
cross-owner isolation. Do not build a substitute store or duplicate runtime
bootstrap. Correct the regeneration report so it says the fenced room omitted
implementation artifacts, while the current v2 repository contains executable
command, event, query, and projection wiring plus restart tests.

Run the session acceptance suite, docs gate, formal contract checks, and
whitespace checks. Report separately what a same-process close/reopen test
proves and what still needs deployment-level qualification.
