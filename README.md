# Agent Workload Platform v2

AWP v2 is a sibling repository for rebuilding AWP one documented slice at a
time. It uses the [Löyly way of working](docs/wow/way-of-working.md): pinned
sources in `raw/`, an implementation-independent [domain wiki](docs/wiki/index.md),
slice [plans and acceptance scenarios](docs/plan/03-session-orchestration/plan.md), and
an independently verified implementation in `impl/`.

Slices [01 task admission](docs/plan/01-task-admission/plan.md) and
[02 task lifecycle](docs/plan/02-task-lifecycle/plan.md) have Grain implementations
and acceptance evidence. The current planned slice covers the
[persistent orchestrator conversation](docs/plan/03-session-orchestration/plan.md)
and its [behavioral model](specs/session-orchestration.allium). Its Grain build
has not started. Runtime choices for this slice are recorded in
[implementation notes](impl/session-orchestration-notes.md).

## Read order

1. [Method](docs/wow/way-of-working.md) and [invariants](docs/wow/invariants.md)
2. [Domain wiki](docs/wiki/index.md)
3. [Current slice](docs/plan/03-session-orchestration/plan.md)
4. [Pinned v1 evidence](raw/v1/manifest.md), when checking a claim

Run `harness/verify.sh` to ratify implemented slices 01 and 02; it does not yet
ratify planned slice 03. Run
`harness/fence.sh /path/to/new-room` to create a clean-room checkout for a
later regeneration experiment.
