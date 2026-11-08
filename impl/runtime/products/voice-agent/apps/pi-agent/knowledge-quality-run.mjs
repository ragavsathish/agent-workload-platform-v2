import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { readFile, open, realpath } from "node:fs/promises";
import { resolve, dirname, sep } from "node:path";
import { fileURLToPath } from "node:url";
import {
  assessReadiness, scoreBranches, freezeRun, consumeHoldout,
  writePrivateReport, publicSummary, qualificationStatus, digestJson, selectPartition,
} from "./knowledge-quality-v2.mjs";

const root = resolve(import.meta.dirname, "../../../../../..");
const policyPath = resolve(root, "impl/qualification/knowledge-quality-policy-v1.json");
const workerPath = resolve(import.meta.dirname, "dist/knowledge-worker-cli.js");
const implementationDir = resolve(root, "impl");
const digest = (value) => createHash("sha256").update(value).digest("hex");

export async function verifyRuntimePins(run) {
  try {
    const { defaultServices } = await import("./dist/knowledge-worker.js");
    const services = defaultServices();
    const [journalPrompt, candidatePrompt, workerCode, scorerCode, orcCode] = await Promise.all([
      readFile(resolve(import.meta.dirname, "dist/knowledge/prompts/journal.md")),
      readFile(resolve(import.meta.dirname, "dist/knowledge/prompts/candidate.md")),
      readFile(resolve(import.meta.dirname, "dist/knowledge-worker.js")),
      readFile(resolve(import.meta.dirname, "knowledge-quality-v2.mjs")),
      readFile(resolve(implementationDir, "src/awp_v2/knowledge/orc_adapter.clj")),
    ]);
    return services.model === run.modelId && services.classifierModel === run.classifierId
      && services.reviewThreshold === run.classifierThreshold
      && run.promptId === `sha256:${digest(Buffer.concat([journalPrompt, candidatePrompt]))}`
      && run.workerDigest === `sha256:${digest(workerCode)}`
      && run.scorerId === `sha256:${digest(scorerCode)}`
      && run.orcAdapterDigest === `sha256:${digest(orcCode)}`;
  } catch { return false; }
}

async function privateFile(path) {
  const full = await realpath(path);
  if (full === root || full.startsWith(`${root}${sep}`)) throw new Error("private input must be outside repository");
  return full;
}

function child(command, args, stdin, timeoutMs, cwd) {
  return new Promise((done) => {
    const childProcess = spawn(command, args, { cwd, stdio: ["pipe", "pipe", "pipe"], env: process.env });
    let stdout = ""; let stderr = ""; let completed = false;
    const timer = setTimeout(() => childProcess.kill("SIGKILL"), timeoutMs);
    const finish = (result) => { if (completed) return; completed = true; clearTimeout(timer); done(result); };
    childProcess.stdout.on("data", (chunk) => { stdout += chunk; if (stdout.length > 1_000_000) childProcess.kill("SIGKILL"); });
    childProcess.stderr.on("data", (chunk) => { stderr += chunk; if (stderr.length > 1_000_000) childProcess.kill("SIGKILL"); });
    childProcess.on("error", () => finish({ status: "failed", stdout: "" }));
    childProcess.on("close", (code) => finish({ status: code === 0 ? "completed" : "failed", stdout }));
    childProcess.stdin.end(stdin);
  });
}

export async function observeNative(item, run) {
  const work = { owner: item.owner, sourceId: item.sourceId,
    taskId: `quality-${digest(`${run.configDigest}:${item.id}`).slice(0, 24)}`,
    mode: item.branch === "journal" ? "journal" : "classification",
    transcript: item.sourceText, capturedAt: item.capturedAt ?? "2026-11-08T00:00:00Z" };
  const result = await child(process.execPath, [workerPath, "--emit-result"], JSON.stringify(work), 180_000, import.meta.dirname);
  if (result.status !== "completed") return { caseId: item.id, branch: item.branch, owner: item.owner, status: "failed", predictions: [] };
  try {
    const body = JSON.parse(result.stdout);
    const predictions = item.branch === "journal"
      ? (body.suggestions ?? []).map((entry) => ({ kind: entry["entry-type"], text: entry.text,
        evidence: entry.evidence, owner: item.owner }))
      : [{ kind: body.classification?.kind ?? "none", evidence: body.suggestions?.[0]?.evidence ?? "",
           text: body.suggestions?.[0]?.text ?? "", owner: item.owner }];
    return { caseId: item.id, branch: item.branch, owner: item.owner, status: "completed", predictions };
  } catch { return { caseId: item.id, branch: item.branch, owner: item.owner, status: "failed", predictions: [] }; }
}

export async function observeOrc(item, run) {
  const request = { id: item.id, owner: item.owner, sourceText: item.sourceText, modelId: run.modelId,
    branch: item.branch, endpointConcepts: item.endpointConcepts,
    promptId: run.promptId, policyId: run.policyId };
  const result = await child("clojure", ["-M", "-m", "awp-v2.knowledge.quality-orc-runner"],
    JSON.stringify(request), 180_000, implementationDir);
  const marker = result.stdout.lastIndexOf("AWP_QUALITY_RESULT:");
  if (result.status !== "completed" || marker < 0) return { caseId: item.id, branch: item.branch, owner: item.owner,
    status: "failed", predictions: [] };
  try {
    const answer = JSON.parse(result.stdout.slice(marker + "AWP_QUALITY_RESULT:".length).trim());
    return { caseId: item.id, branch: item.branch, owner: item.owner,
      status: answer.status, predictions: answer.predictions ?? [] };
  } catch { return { caseId: item.id, branch: item.branch, owner: item.owner, status: "failed", predictions: [] }; }
}

export async function verifyGrainReviewLedger(selectedCases, run) {
  const storageDir = process.env.AWP_QUALITY_GRAIN_STORAGE_DIR;
  if (!storageDir) return { status: "open", reason: "grain-review-ledger-unverified" };
  try { await privateFile(storageDir); }
  catch { return { status: "open", reason: "grain-review-ledger-unverified" }; }
  const request = { owner: run.owner, runId: run.runId, partition: run.partition,
    policyDigest: run.policyDigest, corpusDigest: run.corpusDigest,
    configDigest: run.configDigest, baselineId: run.baselineId,
    candidateId: run.candidateId, rollbackId: run.rollbackId,
    cases: selectedCases.map((item) => ({ id: item.id, sourceDigest: digestJson(item.sourceText),
      expectedDigest: digestJson(item.expected) })) };
  const result = await child("clojure", ["-M", "-m", "awp-v2.knowledge.quality-ledger-gate"],
    JSON.stringify(request), 60_000, implementationDir);
  const marker = result.stdout.lastIndexOf("AWP_LEDGER_RESULT:");
  if (result.status !== "completed" || marker < 0) return { status: "open", reason: "grain-review-ledger-unverified" };
  try { return JSON.parse(result.stdout.slice(marker + "AWP_LEDGER_RESULT:".length).trim()); }
  catch { return { status: "open", reason: "grain-review-ledger-unverified" }; }
}

export function inspectPendingDrafts(drafts) {
  return { status: "open", sources: drafts.map((draft) => ({
    cases: Array.isArray(draft?.cases) ? draft.cases.length : 0,
    pending: Array.isArray(draft?.cases) ? draft.cases.filter((item) => item.annotationStatus === "pending").length : 0,
    privacyStatus: typeof draft?.privacyReview === "string" ? draft.privacyReview : draft?.privacyReview?.status ?? "missing",
    splits: Object.fromEntries([...new Set((draft?.cases ?? []).map((item) => item.split ?? "unassigned"))]
      .map((split) => [split, draft.cases.filter((item) => (item.split ?? "unassigned") === split).length])),
  })) };
}

export function preparePendingCandidates(drafts) {
  const cases = drafts.flatMap((draft, sourceIndex) => (draft?.cases ?? []).map((item, index) => ({
    id: `legacy-${sourceIndex + 1}-${index + 1}`,
    owner: "unverified-owner", sourceId: item.messageId ?? item.id ?? `legacy-source-${index + 1}`,
    sourceText: item.transcript, branch: draft.purpose === "classifier_owner_turn_annotation_draft" ? "assertion" : "journal",
    semanticFamilyId: item.semanticFamilyId ?? `unassigned-${sourceIndex + 1}-${index + 1}`,
    split: item.split ?? "unassigned", sourceKind: item.sourceKind ?? "unverified",
    errorSlice: item.errorSlice ?? "unassigned", sampleKind: "unassigned",
    annotationStatus: "pending", privacyReview: { status: "pending" }, reviews: [], expected: null,
  })));
  return { formatVersion: 2, datasetId: "pending-legacy-candidates", cases };
}

async function main() {
  const [mode, ...args] = process.argv.slice(2);
  if (mode === "inspect-pending") {
    if (args.length < 2) throw new Error("two private draft paths required");
    const drafts = [];
    for (const value of args) drafts.push(JSON.parse(await readFile(await privateFile(value), "utf8")));
    process.stdout.write(`${JSON.stringify(inspectPendingDrafts(drafts))}\n`);
    return;
  }
  if (mode === "prepare-pending") {
    if (args.length !== 3) throw new Error("two private draft paths and a private output path required");
    const drafts = [];
    for (const value of args.slice(0, 2)) drafts.push(JSON.parse(await readFile(await privateFile(value), "utf8")));
    const result = preparePendingCandidates(drafts);
    await writePrivateReport(args[2], result);
    process.stdout.write(`${JSON.stringify({ status: "pending", cases: result.cases.length })}\n`);
    return;
  }
  if (mode === "readiness") {
    if (args.length !== 2) throw new Error("private corpus and private summary paths required");
    const corpus = JSON.parse(await readFile(await privateFile(args[0]), "utf8"));
    const policy = JSON.parse(await readFile(policyPath, "utf8"));
    const readiness = assessReadiness(corpus, policy);
    const summary = { status: readiness.status, reasons: readiness.reasons,
      cases: readiness.cases, eligibleCases: readiness.eligibleCases,
      semanticFamilies: readiness.semanticFamilies,
      branches: readiness.branches };
    await writePrivateReport(args[1], summary);
    process.stdout.write(`${JSON.stringify({ status: readiness.status,
      cases: readiness.cases, eligibleCases: readiness.eligibleCases, reasons: readiness.reasons })}\n`);
    return;
  }
  if (mode !== "evaluate" || args.length !== 4) throw new Error("usage: evaluate <private-corpus> <private-report> <private-summary> <private-holdout-dir>");
  const [corpusArg, reportArg, summaryArg, holdoutDirArg] = args;
  const corpus = JSON.parse(await readFile(await privateFile(corpusArg), "utf8"));
  const policy = JSON.parse(await readFile(policyPath, "utf8"));
  const readiness = assessReadiness(corpus, policy);
  const runConfig = corpus.runConfig;
  if (!runConfig || !runConfig.runId || !runConfig.owner || !runConfig.partition
      || !runConfig.baselineId || !runConfig.candidateId || !runConfig.rollbackId
      || !runConfig.modelId || !runConfig.promptId || !runConfig.taxonomyId || !runConfig.scorerId
      || !runConfig.classifierId || !Number.isFinite(runConfig.classifierThreshold)
      || !runConfig.workerDigest || !runConfig.orcAdapterDigest) {
    throw new Error("frozen run configuration required");
  }
  const run = freezeRun({ policy, corpus, ...runConfig });
  let selectedCases = [];
  try { selectedCases = selectPartition(corpus.cases, run.partition, run.owner); }
  catch {
    readiness.status = "open";
    readiness.reasons.push("partition-or-owner-mismatch");
  }
  if (readiness.status === "ready" && !await verifyRuntimePins(run)) {
    readiness.status = "open";
    readiness.reasons.push("runtime-pins-mismatch");
  }
  if (readiness.status === "ready") {
    const ledger = await verifyGrainReviewLedger(selectedCases, run);
    if (ledger.status !== "ready") {
      readiness.status = "open";
      readiness.reasons.push(ledger.reason ?? "grain-review-ledger-unverified");
    }
  }
  let score = null;
  if (readiness.status === "ready") {
    if (run.partition === "locked_holdout") {
      await privateFile(holdoutDirArg);
      await consumeHoldout(holdoutDirArg, run);
    }
    const observations = [];
    for (const item of selectedCases) observations.push(item.branch === "journal" || item.branch === "assertion"
      ? await observeNative(item, run) : await observeOrc(item, run));
    score = scoreBranches(selectedCases, observations, policy);
    await writePrivateReport(reportArg, { run, readiness, observations, score });
  }
  const verdict = qualificationStatus({ readiness, score, migration: "open", deployment: "open",
    rollbackId: run.rollbackId, policy });
  const summary = { ...publicSummary({ score, readiness, run }), verdict };
  await writePrivateReport(summaryArg, summary);
  process.stdout.write(`${JSON.stringify({ status: verdict.modelQuality, readiness: readiness.status,
    reasons: readiness.reasons, cases: readiness.cases })}\n`);
  if (verdict.modelQuality !== "passed") process.exitCode = 2;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(() => { process.stderr.write("knowledge quality evaluation failed; inspect private log\n"); process.exitCode = 1; });
}
