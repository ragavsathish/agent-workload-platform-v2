# Build harness

Cites [the invariants](invariants.md) and [the current slice](../plan/08-knowledge-extraction-migration/plan.md).

Planning and implementation are separate invocations. A planning invocation
changes the wiki, plan, scenarios, tickets, or decisions. A build invocation
reads them and changes `impl/` within the named ticket. The builder records a
failing acceptance run before making it pass. A spec change during a build
requires a new planning invocation and a new baseline.

The independent `harness/verify.sh` runs documentation checks, behavioral
specification checks, the implemented acceptance suites, and the public module
probe. Slice 05 media and deployed qualification coverage are added during its build.
`harness/fence.sh` can produce a fresh single-commit checkout without prior
implementation or measurement history for a regeneration run.
