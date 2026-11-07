# Ticket 03: outcome and playback

Cites the [plan](plan.md), [personal song rules](../../wiki/personal-song.md), [source lifecycle](../../../raw/v1/song-lifecycle.py), and [acceptance scenarios](spec.feature).

Project lyric and render task outcomes into one bounded generation view. Expose a private playable artifact only after successful render and verified storage. Keep failures bounded. Cancel both work stages where applicable, and suppress late completion after cancellation. Enforce owner scope for status, decision, render, cancel, and playback.

Acceptance: [song-007](spec.feature), [song-008](spec.feature), and [song-009](spec.feature).
