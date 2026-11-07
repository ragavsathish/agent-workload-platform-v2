# Ticket 02: service start and task scheduling

Cites the [slice plan](plan.md), [public deployment rules](../../wiki/public-deployment.md), [scenarios](spec.feature), [source service lifecycle](../../../raw/v1/voice-makefile), and [source task worker service](../../../raw/v1/service-task-worker.plist).

Provide repeatable service installation, start, stop, status, and restart. Make public readiness reflect required dependencies. Preserve persistent owner selection, accepted turns, task identities, and attempt ownership across process restarts. Run an independent executor that discovers eligible accepted tasks, observes capacity, and either starts one bounded attempt or exposes why it cannot. Acceptance: `deploy-005` through `deploy-007` and `deploy-009`.

Test the restart with a real completed turn and the executor with a newly admitted task; do not seed an already-running attempt as the scheduling proof. Record named red results before implementation and capture status before and after restart.
