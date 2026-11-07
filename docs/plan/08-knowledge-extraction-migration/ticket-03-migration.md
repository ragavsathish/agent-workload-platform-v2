# Ticket 03: existing data migration

Cites [slice plan](plan.md), [source graph operations](../../../raw/v1/knowledge-graph-ops.py), [source graph migration](../../../raw/v1/knowledge-projection-migration.ru), [source graph routes](../../../raw/v1/gateway-main.py), and [migration test](../../../raw/v1/knowledge-gateway-migration-test.py).

Inventory all named collections and legacy shapes without changing them. Freeze and back up a source snapshot; record counts, digests, mapping rules, and checkpoint. Import source, extraction, review, accepted, and daily records while retaining legacy identities and chronology. Quarantine uncertain ownership or provenance. Replay and resume must be idempotent. Reconcile each resource class, current interpretation, and owner-visible sample reads. Rehearse rollback and keep the source snapshot until cutover evidence is approved. Start with the named migration scenarios in the [feature](spec.feature).
