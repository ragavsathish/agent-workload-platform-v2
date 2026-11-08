# Ticket 01: durable lyrics and owner review

Cites the [plan](plan.md), [durable song journey](../../wiki/durable-song-journey.md), [source manifest rules](../../../raw/v1/song-generation.py), and [acceptance scenarios](spec.feature).

Bind a stable accepted lyric task to the owner request and selected workspace. Execute its work through the actual task owner and worker. Validate the structured result and its lineage before offering an immutable manifest and digest for review. A missing or malformed result produces a bounded failure or pending state without invented lyrics.

Acceptance: [durable-song-001](spec.feature), [durable-song-002](spec.feature), and [durable-song-006](spec.feature).
