# Slice 37: persist deployment readiness accurately

Cites the [deployment-level rules](../../wiki/deployment-levels.md), the [local deployment runbook](../../../impl/deployment/README.md), and the [deployment level slice](../23-deployment-levels/plan.md).

## Goal

Persist a truthful readiness observation when the local supervisor reports the health of the services required by the selected deployment level.

## Boundary

The slice begins with an observation of active services and ends with an owner-readable deployment status. Required services that are healthy produce `ready`; a missing or unhealthy required service produces `degraded`; an explicit `starting` or `not_ready` observation retains that state. The owner status must agree with the supervisor's observed service map.

## Acceptance

- An HTTP readiness observation with every required service healthy is persisted as ready.
- A missing or unhealthy required service is persisted as degraded, even if the caller's reported summary says ready.
- Explicit starting and not-ready reports remain visible.
- The owner status is stored durably and survives reopening the deployment projection.

## Verification

Record a failing named acceptance scenario using the same nested service-map shape produced by the live owner observation path. Verify healthy and degraded observations, replay from the durable event record, the formal contract verifier, documentation, local links, and whitespace.

## Documents

- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-readiness-observation.md)
