# Slice 20 relation model reliability qualification

Cites the [slice plan](../../docs/plan/20-relation-model-reliability/plan.md), [scenarios](../../docs/plan/20-relation-model-reliability/spec.feature), [diagnosis](../relation-model-reliability-diagnosis.md), and [slice 19 report](grounded-relations-report.md).

## Observed full path

All live runs used a fresh local owner state, one synthetic source explicitly stating a subject hierarchy, two accepted synthetic endpoint concepts, the pinned `qwen3.5:4b-mlx` model, `subject-relation-prompt-v1`, and `subject-v1`. The command was `clojure -J--add-opens=java.base/java.nio=ALL-UNNAMED -J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED -M -m awp-v2.knowledge.relation-qualification` from `impl/`. The output is a safe summary; raw source and provider responses are not included.

| Run | Output mode and bound | Result | Elapsed | Trace | Pending | Current |
| --- | --- | --- | ---: | --- | ---: | ---: |
| 1 | marker, 150 s | completed and staged | 77.3 s | present | 1 | 0 |
| 2 | marker, 150 s | timeout | 150.3 s | present | 0 | 0 |
| 3 | marker, 210 s | output-schema failure | 98.8 s | present | 0 | 0 |
| 4 | tool-backed, 210 s | completed and staged | 11.7 s | present | 1 | 0 |
| 5 | tool-backed, 210 s | completed and staged | 10.3 s | present | 1 | 0 |
| 6 | tool-backed, 210 s | completed and staged | 29.0 s | present | 1 | 0 |

Run 2 overlapped the local Grain acceptance suite, so contention may have contributed to the timeout. A separate synthetic diagnostic captured the run 3 failure: the model returned a JSON relation array as raw text, while ORC's marker parser could not extract the named `:relations` write. This is why a candidate-looking raw response did not stage. Only the bounded relation node was changed to ORC's tool-backed structured output mode. The three subsequent independent runs each completed through ORC inference, Grain attempt recording, and pending staging. They do not establish a population reliability rate or semantic accuracy.

## Correctness and rollback

Failed, timed-out, invalid, and empty attempts are distinct owner-scoped outcomes with allowlisted categories and trace references. Explicit retry identity permits a new attempt against unchanged source and endpoints; same-identity replay does not re-invoke the model, and changed request content conflicts. A completed attempt stages grounded relations as pending. No model result creates a current edge without owner acceptance. Owner isolation, endpoint provenance, restart, retained history, and rollback to the previous model configuration are covered by the named acceptance suite.

The previous marker-mode version remains available as the Git parent of the slice 20 build. Rollback must preserve the event store and existing owner decisions. An earlier failed result identity remains immutable; an owner-requested new identity is needed for another attempt. The bounded execution limit is 210 seconds by default, configurable only within 1–240 seconds; a timeout remains an open outcome, not a negative relation answer.

## Gates and limits

The full `harness/verify.sh` gate passed after the implementation changes: documentation and source-hash checks, Allium, 165 Clojure tests with 702 assertions, runtime topology, deployment probes, and the public module probe. `git diff --check` found no whitespace errors.

The live staging integration gate passed in three independent synthetic runs using tool-backed output. The reviewed semantic relation quality gate remains **open**: no eligible independent relation corpus and frozen threshold run is available. Private owner cutover and external tailnet qualification remain separate and open. A real owner relation is never inferred or published from these synthetic observations.
