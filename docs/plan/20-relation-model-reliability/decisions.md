# Slice 20 decisions

Cites [the plan](plan.md), [grounded relation rules](../../wiki/knowledge-relations.md), and [slice 19 decisions](../19-grounded-relations/decisions.md).

## D85: failed attempts are immutable but retryable

A replay of the same attempt identity returns its recorded result without another model call. A deliberate retry has a new identity even when the source, endpoints, model, prompt, and policy have not changed. The failed attempt stays in history. This extends the prior identity rule, which did not provide a way to distinguish replay from a new attempt after transient failure.

## D86: failure detail is bounded and owner scoped

The owner can see a safe category and trace reference for a failed attempt. Provider response bodies, source text, credentials, and internal exception detail are kept out of shared reports and cross-owner queries. An invalid candidate, valid empty answer, timeout, and execution failure remain distinct.

## D87: path success requires a pending relation

A raw model candidate is an inference observation. Qualification of the proposal path requires the same attempt to be durably completed and to stage a grounded pending relation. Owner acceptance and semantic model quality are separate gates. A failed synthetic run leaves live staging open rather than being reported as a pass.
