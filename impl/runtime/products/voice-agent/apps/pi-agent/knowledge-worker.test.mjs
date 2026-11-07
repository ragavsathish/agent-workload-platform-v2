import assert from "node:assert/strict";
import { test } from "node:test";
import { Effect } from "effect";
import { processWork } from "./dist/knowledge-worker.js";

const base = { owner: "owner@example.test", sourceId: "c:m", taskId: "task-1",
  transcript: "I learned that retries need stable identifiers.", capturedAt: "2026-11-08T10:00:00Z" };
const config = (handler) => ({ modelBase: "http://model.test", model: "model-1",
  classifierBase: "http://classifier.test", classifierModel: "classifier-1", reviewThreshold: 0.6,
  promptDir: new URL("./dist/knowledge/prompts/", import.meta.url),
  fetch: async (url, init) => ({ ok: true, json: async () => handler(url, JSON.parse(init.body)) }),
});
const model = (value) => ({ message: { content: JSON.stringify(value) } });

test("journal result keeps exact source evidence", async () => {
  const work = { ...base, mode: "journal" };
  const services = config((_url, request) => {
    assert.equal(request.model, "model-1");
    return model({ entries: [
      { type: "learning", text: "Retries need stable identifiers", topics: ["retries"], evidenceQuote: base.transcript },
      { type: "learning", text: "Invented insight", topics: [], evidenceQuote: "not in source" },
    ] });
  });
  const result = await Effect.runPromise(processWork(work, services));
  assert.equal(result.status, "completed");
  assert.equal(result.suggestions.length, 1);
  assert.equal(result.suggestions[0].evidence, base.transcript);
  assert.equal(result.sourceId, "c:m");
});

test("routine command yields visible empty result without model call", async () => {
  const result = await Effect.runPromise(processWork({ ...base, mode: "journal", transcript: "Check the logs." },
    config(() => { throw new Error("should not invoke model"); })));
  assert.equal(result.outcome, "filtered");
  assert.deepEqual(result.suggestions, []);
});

test("classification low confidence keeps decision and no candidate", async () => {
  const result = await Effect.runPromise(processWork({ ...base, mode: "classification" },
    config((url) => {
      assert.ok(url.includes("classifier"));
      return { answers: { assertion_kind: { choice: "skill_evidence", confidence: 0.55, probabilities: {} },
        profile_field: { choice: "none", confidence: 0.99 } } };
    })));
  assert.equal(result.outcome, "no-candidate");
  assert.deepEqual(result.suggestions, []);
  assert.equal(result.classification.confidence, 0.55);
});

test("daily writer validates source local date and prose", async () => {
  const work = { owner: base.owner, taskId: "daily-task", accountId: "daily-1", mode: "daily-account",
    journalDate: "2026-11-08", timeZone: "Europe/Helsinki",
    sources: [{ sourceRef: "c:m", speaker: "owner", capturedAt: base.capturedAt, transcript: base.transcript }] };
  const result = await Effect.runPromise(processWork(work, config(() => model({ title: "A day", story: "I learned about stable identifiers." }))));
  assert.equal(result.accountId, "daily-1");
  assert.equal(result.sources.length, 1);
  await assert.rejects(Effect.runPromise(processWork({ ...work, journalDate: "2026-11-07" }, config(() => model({ title: "A", story: "B" })))));
});
