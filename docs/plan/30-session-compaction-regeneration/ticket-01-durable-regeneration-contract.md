# Close the session compaction regeneration gaps

Use the [Slice 30 plan](plan.md), [acceptance scenarios](spec.feature), the
[root build contract](../../BUILD-CONTRACT.md),
[pinned AWP v1 evidence](../../raw/v1/manifest.md), and
[pinned ORC evidence](../../raw/orc/manifest.md).

Update Slice 29's durable acceptance wording to queue an active-turn request
under its original command identity, promote it after turn settlement, and
revalidate before starting the Pi effect. Align the session wiki and formal
session contract with that flow. Replace implementation-tree citations in the Slice 29 plan and
ticket with durable source evidence. Record only runtime ownership obligations
needed to rebuild: trusted owner context, bootstrap and schema registration,
owner-wide session mutation lock, and rebuildable session projections. Keep
secrets and unsupported client protocol assumptions out of the contract.

Check new scenario IDs against existing acceptance test tags, formal contract
names, docs gate checks, and the session acceptance suite. Run the docs and
formal-contract gates
and `git diff --check`; report checks the available workspace cannot run.
