import { createHash } from "node:crypto";
import { readFile, open } from "node:fs/promises";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { Effect } from "effect";
import { defaultServices, processWork } from "./dist/knowledge-worker.js";

const repoRoot = resolve(import.meta.dirname, "../../../../../..");
function privatePath(value) {
  const path = resolve(value);
  if (path === repoRoot || path.startsWith(`${repoRoot}/`)) throw new Error("private quality files must be outside the repository");
  return path;
}
const digest = (value) => createHash("sha256").update(JSON.stringify(value)).digest("hex");
export function score(cases, outputs, thresholds) {
  if (cases.length !== outputs.length) throw new Error("quality output count mismatch");
  let truePositives = 0; let falseAdditions = 0; let misses = 0; let evidenceErrors = 0; let categoryErrors = 0;
  for (let i = 0; i < cases.length; i += 1) {
    const expected = cases[i].expected ?? [];
    const actual = outputs[i].suggestions ?? [];
    const used = new Set();
    for (const item of actual) {
      const found = expected.findIndex((candidate, index) => !used.has(index)
        && candidate.kind === item.kind && candidate.evidence === item.evidence);
      if (found >= 0) { truePositives += 1; used.add(found); }
      else { falseAdditions += 1; if (expected.some((candidate) => candidate.evidence === item.evidence)) categoryErrors += 1;
        else evidenceErrors += 1; }
    }
    misses += expected.length - used.size;
    if (cases[i].expectedClassification !== undefined
        && outputs[i].classification?.kind !== cases[i].expectedClassification) categoryErrors += 1;
  }
  const precision = truePositives + falseAdditions === 0 ? 1 : truePositives / (truePositives + falseAdditions);
  const recall = truePositives + misses === 0 ? 1 : truePositives / (truePositives + misses);
  const evidenceAccuracy = truePositives + evidenceErrors === 0 ? 1 : truePositives / (truePositives + evidenceErrors);
  const passed = precision >= thresholds.minPrecision && recall >= thresholds.minRecall
    && evidenceAccuracy >= thresholds.minEvidenceAccuracy && categoryErrors <= thresholds.maxCategoryErrors;
  return { status: passed ? "passed" : "open", cases: cases.length, truePositives, falseAdditions, misses,
    evidenceErrors, categoryErrors, precision, recall, evidenceAccuracy };
}

async function main() {
  const [corpusArg, reportArg] = process.argv.slice(2);
  if (!corpusArg || !reportArg) throw new Error("usage: node knowledge-quality.mjs <reviewed-corpus.json> <private-report.json>");
  const corpusPath = privatePath(corpusArg); const reportPath = privatePath(reportArg);
  const corpus = JSON.parse(await readFile(corpusPath, "utf8"));
  if (!corpus.reviewedBy || !corpus.version || !Array.isArray(corpus.cases) || !corpus.cases.length
      || !corpus.cases.every((item) => item.reviewed === true && item.work && Array.isArray(item.expected))
      || !corpus.cases.some((item) => item.expected.length === 0)
      || !corpus.cases.some((item) => item.expected.length > 0)
      || !corpus.cases.some((item) => item.work.mode === "classification")) throw new Error("reviewed positive, negative, and classification cases required");
  const thresholds = corpus.thresholds;
  if (!thresholds || !["minPrecision", "minRecall", "minEvidenceAccuracy", "maxCategoryErrors"].every((key) => Number.isFinite(thresholds[key])))
    throw new Error("reviewed thresholds required");
  const services = defaultServices();
  const outputs = [];
  for (const item of corpus.cases) outputs.push(await Effect.runPromise(processWork(item.work, services)));
  const metrics = score(corpus.cases, outputs, thresholds);
  const report = { ...metrics, corpusVersion: corpus.version, corpusDigest: digest(corpus),
    reviewedBy: corpus.reviewedBy, model: services.model, classifierModel: services.classifierModel,
    processorVersion: outputs[0]?.processorVersion, threshold: services.reviewThreshold,
    thresholds, evaluatedAt: new Date().toISOString() };
  const handle = await open(reportPath, "wx", 0o600);
  try { await handle.writeFile(`${JSON.stringify(report, null, 2)}\n`); } finally { await handle.close(); }
  process.stdout.write(`${JSON.stringify({ status: report.status, cases: report.cases, corpusDigest: report.corpusDigest })}\n`);
  if (report.status !== "passed") process.exitCode = 1;
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => { process.stderr.write(`${String(error)}\n`); process.exitCode = 1; });
}
