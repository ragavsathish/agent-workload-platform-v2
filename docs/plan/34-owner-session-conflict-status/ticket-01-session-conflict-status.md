# Preserve session conflict status at voice ingress

Use the [Slice 34 plan](plan.md), [acceptance scenarios](spec.feature),
[decisions](decisions.md), the Pi live protocol test, and the owner turn route.

Introduce a typed internal session error that preserves HTTP status and
stable error code. Map only known owner-session conflicts to 409 at non-stream
turn ingress and in a safe stream error event when headers already exist.
Keep accepted-turn Pi/provider failures at 502 and preserve authentication and
request-validation handling. Prove a held compaction refuses before model
dispatch, and prove a provider failure is not reclassified as a conflict.

Run the Pi live protocol test, session suite if the internal error contract
changes, the specification validator, docs gate, link checks, and whitespace checks.
