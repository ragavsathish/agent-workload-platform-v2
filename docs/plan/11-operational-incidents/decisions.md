# Slice 11 decisions

Cites [slice plan](plan.md), [incident rules](../../wiki/operational-incidents.md), [source monitor](../../../raw/v1/stack-alert-monitor.py), [source checks](../../../raw/v1/stack-alert-monitor-test.py), and [source contract](../../../raw/v1/c4-operational-incidents.feature).

## D42: durable incident intent precedes task submission

Record the incident epoch and immutable request intent before asking task admission. Retry an uncertain submission with the same request identity and instruction. The task authority deduplicates admission. This closes the source monitor's crash window between owner acceptance and the row's submitted flag. The pruned branch is a transient submission whose identity exists only in a poll process.

## D43: observation freshness is separate from alert state

Only a successful complete alert-list observation can resolve a missing alert. Failed polls retain prior active state and mark observation stale. Three consecutive failures create a managed alert-service outage epoch; a successful poll resets the streak. The pruned branch is treating missing data as an empty healthy alert list.

## D44: incident history is an owner query, not an investigation result

Provide an owner-scoped joined read over incident decisions and task status/outcome. The incident authority owns eligibility, epoch, and request identity; the task authority owns admission, execution, progress, and outcome. Rebuild the read without replaying submissions. The pruned branch is making the investigation agent's prose authoritative over incident state.

## D45: findings are advisory

Keep instructions bounded and read-only. Preserve investigation failure and uncertainty distinctly from a finding. An owner may close or follow up; an alert or model answer alone cannot authorize remediation or a product change. The pruned branch is automatic repair based on a firing alert.
