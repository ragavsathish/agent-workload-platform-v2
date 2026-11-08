# Slice 11: operational incident investigations

Cites [incident rules](../../wiki/operational-incidents.md), [task admission](../../wiki/tasking.md), [task lifecycle](../../wiki/task-lifecycle.md), [source incident contract](../../../raw/v1/c4-operational-incidents.feature), and [decisions](decisions.md).

## Goal and boundary

Turn approved firing alerts into one bounded, read-only investigation per incident epoch, with a durable request identity and an owner-visible incident history. The [scenarios](spec.feature) define observable behavior from fresh state.

The slice starts with an alert-list observation or alert-service failure and ends at a refused observation, retained epoch, accepted investigation task, visible finding/failure, or resolved alert. It reuses the existing task authority. It does not remediate services, edit product code, or nominate a change. External alert delivery, metric availability, and the investigator's factual quality require separate deployed qualification.

## Tickets

- [Ticket 01](ticket-01-incident-epochs.md): approved rules, fingerprints, active/resolved epochs, cooldown, and alert-service outage.
- [Ticket 02](ticket-02-investigation-request.md): bounded evidence, immutable instruction, stable request identity, retry and task authority.
- [Ticket 03](ticket-03-owner-query.md): incident/task projection, owner isolation, failure visibility, rebuild and restart behavior.

## Exit evidence

Record named failing acceptance runs before implementation, then passing runs and the independent gate. Exercise alert and metric adapters with controlled responses, including real request submission through the task boundary. Prove a restart after request intent but before acknowledgement yields one task. A live alert-service and metric-source probe may qualify integration; until then external monitoring remains unqualified. Technology notes belong in [implementation notes](../../../impl/operational-incidents-notes.md).
