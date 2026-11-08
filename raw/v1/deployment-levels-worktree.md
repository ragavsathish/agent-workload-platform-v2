# Source capture: deployment levels

This source capture records an uncommitted v1 worktree change observed on
2026-11-08. It is based on v1 commit
`d8afdfc31ea3ac7d74eef625c8863f5954b75fcd`; it is not part of that commit or a
claim that the v1 change has been released.

The adjacent [patch](deployment-levels-worktree.patch) contains only changes to
the deployment-level implementation and its operator guidance. It adds
`voice`, `durable`, and `full` selections; chooses host task execution for
`voice` and `durable`, Gondolin task execution for `full`; adds a full-only
Compose profile; and starts or stops host services according to the selection.
The persistent voice sandbox remains Gondolin-backed at every level.

The captured patch SHA-256 is
`c40d3afdc930e160bf965e42dd04cc82ff1e91523d7af3109b22908242a9956b`.
The source working tree remains authoritative for its subsequent changes; this
snapshot is the evidence used for [slice 23](../../docs/plan/23-deployment-levels/plan.md).
