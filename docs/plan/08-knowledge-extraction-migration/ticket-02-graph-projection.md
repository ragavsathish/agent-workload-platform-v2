# Ticket 02: knowledge projection and repair

Cites [slice plan](plan.md), [source ontology](../../../raw/v1/knowledge-ontology.ttl), [source shapes](../../../raw/v1/knowledge-shapes.ttl), and [source graph routes](../../../raw/v1/gateway-main.py).

Project source messages, suggestions, classification decisions, review actions, accepted facts, and daily versions into owner-scoped reads. Preserve current interpretation and immutable action history. Rebuild the projection from authoritative records after a failed update, then compare identities and counts. Check that a writer credential cannot read or decide as the owner and that unrelated owners' texts never appear. Start with the named projection scenarios in the [feature](spec.feature).
