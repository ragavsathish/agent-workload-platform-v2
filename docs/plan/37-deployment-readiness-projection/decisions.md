# Slice decisions

Cites the [plan](plan.md), [deployment level rules](../../wiki/deployment-levels.md), and [existing deployment decisions](../23-deployment-levels/plan.md).

## D01: readiness is derived from required services

The reported summary is not sufficient evidence on its own. The deployment authority derives ready or degraded from the active level's required service map and preserves explicit starting and not-ready states.

## D02: owner status is the durable observation

The supervisor's process view and the durable owner status must agree after a successful observation. A healthy local process list does not override a degraded durable projection.
