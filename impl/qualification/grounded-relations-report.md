# Slice 19 grounded relation proposal qualification

Cites the [slice plan](../../docs/plan/19-grounded-relations/plan.md), [acceptance scenarios](../../docs/plan/19-grounded-relations/spec.feature), [implementation notes](../grounded-relations-notes.md), and [reviewed quality policy](knowledge-quality-policy-v1.json).

## Structural evidence

- The red baseline failed at the missing bounded relation proposal function before implementation.
- All twelve named relation scenarios are represented in the Grain acceptance suite. The final verifier passed 157 Clojure tests with 626 assertions; the relation quality protocol passed eleven JavaScript tests. Strict Grain/Allium composition, documentation source hashes, and structural Allium checks passed.
- An owner relation attempt records source digest, endpoint identities and revisions, model, prompt, policy, trace, status, and candidates. Completed candidates stage as pending. Changed retries conflict. A separate owner action accepts, rejects, corrects, or revokes. Current graph reads filter retained derived records through current Grain decisions.

## Bounded local model observation

Model `qwen3.5:4b-mlx` was run against one synthetic source with two synthetic accepted subjects. One ORC behavior-tree run returned one candidate and a trace in 122.6 seconds. Two other runs returned a failed status after about 80.8 and 56.4 seconds, respectively. Those failures recorded no pending or accepted relation. A successful inference has not yet been observed through the complete proposal-to-Grain staging path. The run variation is a material reliability finding; this smoke does not qualify semantic accuracy.

## Gates

**Relation model quality: open.** No independently reviewed, privacy-approved relation cases and frozen threshold run are available. No semantic precision, recall, or deployment claim is made from the synthetic example. The private knowledge cutover and external tailnet qualifications remain independent and open.

**Live model staging: open.** The ORC sheet produced one candidate, but the full model-to-Grain path has only observed safe failed attempts. Deterministic staging, review, replay, and projection checks passed.
