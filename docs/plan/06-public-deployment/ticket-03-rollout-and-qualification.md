# Ticket 03: rollout, rollback, and public qualification

Cites the [slice plan](plan.md), [public deployment rules](../../wiki/public-deployment.md), [scenarios](spec.feature), [host contract](../../../raw/v1/host-requirements.md), and [source service lifecycle](../../../raw/v1/voice-makefile).

Define a previous qualified version and an update procedure. Exercise a failed readiness or journey, restore the previous version, and verify the same owner conversation and accepted task through the public route. Run the full public journey from a separate owner device, with genuine timed audio and audible reply, then restart and read. Capture external origin, owner identity decision, actual media path, safe turn/task identifiers, persistent state checks, service versions, and rollback outcome. Acceptance: `deploy-008` and a fresh run of `deploy-001` through `deploy-009` for all available capabilities.

The qualification report distinguishes a public pass from local routing, external setup unavailable, and a genuine test failure. It includes prerequisites and remediation for any unqualified gate. See the [implementation note](../../../impl/public-deployment-notes.md).
