# Ticket 02: worker settlement

Cites [the slice plan](plan.md), [scenarios](spec.feature), and [task result rules](../../wiki/task-result-delivery.md).

Run the real worker against the selected workspace, record bounded progress, capture the resulting work product, and make exactly one terminal transition. Fail execution or capture without advertising bytes. Preserve uncertainty and require an explicit retry after a lost worker; reject late or repeated terminal callbacks. Cover `delivery-001`, `delivery-002`, `delivery-005`, `delivery-006`, and `delivery-007`.
