# Agent Workload Platform v2

AWP v2 is a sibling repository for rebuilding AWP one documented slice at a
time. It uses the [Löyly way of working](docs/wow/way-of-working.md): pinned
sources in `raw/`, an implementation-independent [domain wiki](docs/wiki/index.md),
slice [plans and acceptance scenarios](docs/plan/01-task-admission/plan.md), and
an independently verified implementation in `impl/`.

The first slice admits and queries one owner's task request. Its observable
behavior is in [the acceptance scenarios](docs/plan/01-task-admission/spec.feature)
and [the behavioral model](specs/tasking.allium). Grain is the implementation
framework; its runtime topology and code belong under `impl/`. This slice does
not yet execute work or replace v1.

The next planned slice covers [task lifecycle and execution](docs/plan/02-task-lifecycle/plan.md)
with its [behavioral model](specs/task-lifecycle.allium). It is a planning
baseline; its acceptance tests and implementation have not been built.

## Read order

1. [Method](docs/wow/way-of-working.md) and [invariants](docs/wow/invariants.md)
2. [Domain wiki](docs/wiki/index.md)
3. [Current slice](docs/plan/01-task-admission/plan.md)
4. [Pinned v1 evidence](raw/v1/manifest.md), when checking a claim

Run `harness/verify.sh` to ratify implemented slice 01; it does not yet
ratify planned slice 02. Run
`harness/fence.sh /path/to/new-room` to create a clean-room checkout for a
later regeneration experiment.
