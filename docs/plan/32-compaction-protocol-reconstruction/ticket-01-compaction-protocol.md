# Make compaction protocol and proof reproducible

Use the [Slice 32 plan](plan.md), [acceptance scenarios](spec.feature),
[decision log](decisions.md), [Slice 29 compaction contract](../29-session-compaction-reconciliation/plan.md),
[current runtime protocol tests](../../../impl/runtime/products/voice-agent/apps/pi-agent/live-protocol.test.mjs),
[owner client acceptance tests](../../../impl/runtime/products/voice-agent/apps/client/test/owner_workspace_acceptance_test.dart),
the Pi package declaration and runtime pin, and the
[persistent regeneration report](../30-session-compaction-regeneration/regeneration-delta.txt).

Reconcile current implementation evidence with durable Slice 29 docs. Record
only verified route, header, owner-context fields, command ID recovery rules,
Pi branch types, and the branch predicate exercised by tests. Do not infer an
atomic Pi snapshot or a provider accounting guarantee. Amend the regeneration
report to separate current-v2 implementation proof from limits of the fenced
inputs. No runtime code change is needed unless an acceptance assertion finds
a defect.

Run the Pi live-protocol test, including stale queued eligibility refusal, the
owner client test suite, formal model checks,
docs gate, documentation link checks, and whitespace checks. Keep
client protocol and Pi dependency behavior tied to the exact pinned version.
