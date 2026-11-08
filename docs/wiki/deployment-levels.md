# Deployment levels

Cites the [captured current deployment change](../../raw/v1/deployment-levels-worktree.md),
the [v1 source patch](../../raw/v1/deployment-levels-worktree.patch), and the
[public deployment rules](public-deployment.md).

A **deployment level** selects which installed capabilities are available on
one AWP host. The levels are `voice`, `durable`, and `full`; omitting a level
selects `full`.

- **Voice** runs the core owner conversation and speech capabilities. Durable
  task execution is unavailable at this level. A task request that cannot be
  executed must receive an explicit unavailable answer before it is accepted.
- **Durable** adds owner task management and task execution on the host. Each
  task remains bound to its approved workspace and owner identity.
- **Full** adds the remaining configured integrations and executes durable
  tasks in an isolated workspace. It includes the full-only repository and
  trace-render services when those services are configured.

Pi Voice's persistent tool environment remains isolated at every level. The
task execution level does not change the voice environment, selected owner,
conversation identity, accepted task identity, or recorded histories.

Changing levels stops services outside the selected set and starts the
required services for the new set. It preserves stored sessions, accepted
tasks, review decisions, and artifacts. An in-flight task interrupted by the
transition keeps its identity and requires the existing explicit recovery
action; selecting a level never silently retries it. A level is ready only
after its required capabilities pass health checks. Desired level, active
services, and readiness are reported separately so a failed start cannot appear
ready.

An unsupported level is refused before changing any service. A repeated
selection of the active level does not duplicate effects. These level rules
describe available operating capabilities; they do not grant owner authority
or change the privacy boundary of any underlying query.
