# Ticket 01: managed alerts and incident epochs

Cites [slice plan](plan.md), [incident rules](../../wiki/operational-incidents.md), [source monitor](../../../raw/v1/stack-alert-monitor.py), and [source rule inventory](../../../raw/v1/stack-alerts.json).

Validate each observation against the approved rule inventory, manager, investigation flag, and firing state. Derive a stable fingerprint when absent. Retain the first-seen instant through duplicate polls and short returns after resolution. Start a new epoch only after the cooldown. Keep failed polls from resolving active incidents; open one alert-service outage epoch after three consecutive failures and reset the streak on success.

Acceptance: [incident-001](spec.feature), [incident-002](spec.feature), [incident-003](spec.feature), [incident-004](spec.feature), [incident-005](spec.feature), and [incident-006](spec.feature).
