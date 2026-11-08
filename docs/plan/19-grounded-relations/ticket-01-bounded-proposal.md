# Ticket 01: bounded model relation proposal

Cites [the slice plan](plan.md), [relation rules](../../wiki/knowledge-relations.md), and [scenarios](spec.feature) `relation-001` through `relation-004`.

Given an owned source and accepted endpoint pair, ask the bounded model only for subject hierarchy or association edges, with a declared maximum and deadline. Retain source digest, endpoint identities and revisions, model, prompt, policy version, and trace. Validate exact excerpt and allowed direction/kind; record empty, malformed, unsupported, and timeout outcomes. No model output issues an acceptance decision. Acceptance: `relation-001` through `relation-004`.
