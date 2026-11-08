# Ticket 03: rollback rehearsal and approval artifact

Cites the [continuity rules](../../wiki/private-knowledge-continuity.md), [prior gate](../08-knowledge-extraction-migration/ticket-04-quality-gates.md), and [scenarios](spec.feature).

In an isolated copy, choose shadow owner reads and then return to the prior source. Compare the same owner queries before and after and verify both histories and snapshot persist. Produce a private detailed ledger and a disclosure-reviewed aggregate report. Freeze the report identity and required owner approval as a separate live-switch input. A failed check or absent approval leaves live reads unchanged. Acceptance: `continuity-08` and `continuity-09`.
