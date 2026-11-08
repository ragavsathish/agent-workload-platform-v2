# Ticket 02: exact decision and private render

Cites the [plan](plan.md), [personal song rules](../../wiki/personal-song.md), [source song service](../../../raw/v1/song-service.py), and [acceptance scenarios](spec.feature).

Present the full manifest before a distinct owner decision. Match approval to its digest and refuse render without it. Hand the same approved manifest to one separate render task and expose completed audio only through the owner-checked path. Preserve stable identities across repeated requests.

Acceptance: [durable-song-003](spec.feature), [durable-song-004](spec.feature), and [durable-song-005](spec.feature).
