# Ticket 01: persist the service readiness observation

Cites the [deployment readiness plan](plan.md) and [scenarios](spec.feature).

Normalize the service observation at the deployment command boundary so the live owner route and direct observations use the same required-service rules. Keep incomplete service sets degraded and preserve starting or not-ready states. Add a named regression case that fails before the change, plus durable reopen coverage. Acceptance: `deployment-readiness-001` through `deployment-readiness-003`.
