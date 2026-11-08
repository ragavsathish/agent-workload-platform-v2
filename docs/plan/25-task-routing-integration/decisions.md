# Decisions

Cites the [task routing plan](plan.md), the [task routing rules](../../wiki/task-routing.md), and the [captured session sources](../../../raw/v1/manifest.md).

## D110 — The route is coordinated across two authorities

The selected conversation record and the live task connection are distinct. The owner operation checks ownership and eligibility before the task connection changes. The live connection is established before a successful attachment record is returned. If the recorded route update fails, the service restores the prior connection when possible. Any failed restoration leaves turn dispatch blocked until a read or explicit retry reconciles both sides.

## D111 — Retry identity belongs to the transition

Attach and detach accept a bounded command identity. Replaying an identical completed transition returns its prior result. Reusing that identity with another task or operation is a conflict. A later explicit transition uses a new identity.

## D112 — Restart never invents a live connection

Process-local task connections cannot be recovered from the saved route. Recovery records detached routing before the service accepts another turn. It never opens a task socket merely because the previous process recorded one.
