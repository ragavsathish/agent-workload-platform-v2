import assert from "node:assert/strict";
import { test } from "node:test";
import { score } from "./knowledge-quality.mjs";

test("quality report separates false additions and misses", () => {
  const cases = [{ expected: [{ kind: "journal", evidence: "I learned." }] }, { expected: [] }];
  const outputs = [{ suggestions: [{ kind: "journal", evidence: "I learned." }] },
    { suggestions: [{ kind: "journal", evidence: "Check logs." }] }];
  const result = score(cases, outputs, { minPrecision: 0.9, minRecall: 0.9, minEvidenceAccuracy: 0.9, maxCategoryErrors: 0 });
  assert.equal(result.falseAdditions, 1);
  assert.equal(result.misses, 0);
  assert.equal(result.status, "open");
});
