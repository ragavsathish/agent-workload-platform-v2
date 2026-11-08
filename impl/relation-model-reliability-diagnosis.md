# Slice 20 relation staging diagnosis

Cites [the slice plan](../docs/plan/20-relation-model-reliability/plan.md), [pinned relation observations](../raw/relation-stage-observations.md), [slice 19 qualification](qualification/grounded-relations-report.md), [the bounded relation adapter](src/awp_v2/knowledge/orc_adapter.clj), and [the deterministic replay](diagnostics/relation-stage-replay.clj).

## Pinned observation and loop

Slice 19 ran a synthetic owned source and two accepted synthetic concepts with `qwen3.5:4b-mlx`. One isolated ORC sheet call returned a candidate in 122.6 seconds, but it did not call the proposal-to-Grain staging boundary. Two complete `propose-relations!` calls returned `:failed` in 80.8 and 56.4 seconds. Each recorded one failed attempt and zero pending or current relations. The recorded trace reference was present, but the slice 19 console report did not retain a safe failure category or ORC error. These are distinct observations, not a single successful run.

The red-capable loop drives the full proposal call with the previously observed failed ORC status. From `impl/`:

```sh
AWP_RELATION_REPLAY=failed clojure -J--add-opens=java.base/java.nio=ALL-UNNAMED -J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED -M -i diagnostics/relation-stage-replay.clj
```

Observed red signal: `{:mode "failed", :status :failed, :attempt-count 1, :pending-count 0, :published-count 0, :expected-pending? false}` and exit 1. This replay is synthetic and intentionally fails the desired pending-stage assertion. The paired completed candidate control uses `AWP_RELATION_REPLAY=completed` with the same command and produced `:staged`, one pending relation, zero published, and exit 0. Both runs use a fresh local event store and the same accepted endpoint/source setup. The replay isolates failure-result handling and staging; it does not claim live inference succeeded or identify why ORC failed.

## Ranked, falsifiable hypotheses

1. The local model or provider returns content ORC cannot parse into the declared relation output. A failed live result will carry a schema, parse, or provider error; changing only the output contract or parser behavior will change the failure rate.
2. The ORC leaf exceeds its execution or liveness budget. A failed trace will show timeout, deadline, or stalled leaf state; a bounded larger budget or faster pinned model will change the result while the output shape remains valid.
3. The model returns a candidate that violates the bounded relation contract. ORC will report success, but the adapter will record `:invalid` rather than `:failed`; exact excerpt, endpoint IDs, and kind validation will identify which field fails.
4. The control-plane lifecycle or provider registration differs between isolated and complete calls. With the same synthetic input and model, a direct sheet call succeeds while the complete call fails at a specific boundary; retaining the same control plane or registration will change that difference.

Two synthetic full-path diagnostic runs in slice 20 returned `:completed` from ORC with one candidate, and each same call recorded one completed attempt and one pending relation, zero published. Thus the staging boundary can work, and the slice 19 failure is intermittent in the observed local setup. The second process retained background activity after the completed result and required interruption; the printed result was already durable before interruption. These runs do not establish a live reliability rate or semantic accuracy. The next build turn should capture the safe ORC error and trace category on a failing live run, test the relevant hypothesis one variable at a time, and rerun both the red replay and full live path after the fix. Preserve the unredacted trace only in a private temporary location; do not commit owner content, provider responses, or credentials.

## Build-turn probe and bounded change

The first post-change full-path live run completed in 77.3 seconds and staged one pending relation. The second recorded a trace-backed `:timeout` at the existing 150,000 ms execution bound, with no pending or current relation. The second run overlapped local Grain tests, so contention is a plausible contributor; the evidence does not prove it. This observation supports the execution-budget hypothesis and weakens the claim that staging always fails. The implementation increased the default bound to 210,000 ms, kept a 240,000 ms maximum for explicit configuration, and retained explicit owner retry rather than automatic re-invocation. A focused acceptance check verifies the bound passed to the ORC execution seam and the timeout category. An isolated live run under the longer bound then failed after 98.8 seconds with `:output-schema`; the budget change cannot solve that separate failure mode. These results are recorded in the [qualification report](qualification/relation-model-reliability-report.md).

A private synthetic diagnostic reproduced the schema failure. ORC reported `LLM output unparseable for keys [:relations]` because the model returned a JSON array in raw text without the named output marker. The array used the intended endpoint IDs, kind, and exact synthetic excerpt, but ORC's marker parser never wrote the `:relations` field. The raw response is not committed. The targeted change opts this one bounded relation node into ORC's tool-backed structured output mode. The selected mode is testable through a subsequent full-path synthetic run; the result must still pass deterministic grounding checks and owner review. No attempt is silently retried or published.

Three independent full-path runs under the tool-backed mode completed in 11.7, 10.3, and 29.0 seconds. Each durably recorded one completed attempt with a trace, staged one pending relation, and left current relations empty. This is integration evidence for the targeted fix, not a statistical reliability estimate. The marker-mode failure was reproduced with the raw response kept outside Git; a deterministic fake-model unit cannot reproduce ORC's provider/parser behavior, so the live full-path qualification remains the required regression evidence for that seam.

## Rollback and qualification boundary

The prior model, prompt, and policy identifiers are the rollback configuration. Rolling back does not rewrite an attempt or re-invoke an already recorded identity. A new retry identity may use that prior configuration, and only a separately accepted owner relation becomes current. A live path pass requires a trace-backed completed attempt and a pending relation from the same run. The reviewed semantic quality gate remains open without independently reviewed labels and frozen thresholds.
