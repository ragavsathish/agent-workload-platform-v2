import { createHash } from "node:crypto";
import { open, realpath } from "node:fs/promises";
import { resolve, dirname, join, sep } from "node:path";

const REPO_ROOT = resolve(import.meta.dirname, "../../../../../..");
const BRANCHES = Object.freeze(["journal", "assertion", "concept", "relation"]);
const SPLITS = new Set(["development", "challenge", "locked_holdout", "production_shadow"]);
const assertionKinds = new Set(["none", "conversation_topic", "skill_evidence", "profile_update"]);
const normalize = (value) => value.normalize("NFKC").toLocaleLowerCase("en-US").replace(/\s+/gu, " ").trim();
const ratio = (top, bottom) => bottom === 0 ? null : top / bottom;
const code = (reasons, value) => { if (!reasons.includes(value)) reasons.push(value); };
const nonempty = (value) => typeof value === "string" && value.trim().length > 0;
const safeReference = (value) => nonempty(value) && value.length <= 128 && /^[\w.:-]+$/u.test(value);
const words = (value) => new Set((normalize(value).match(/[\p{L}\p{N}]+/gu) ?? []).slice(0, 500));
function nearDuplicate(left, right) {
  if (!left.size || !right.size) return false;
  let shared = 0;
  for (const word of left) if (right.has(word)) shared += 1;
  return shared / (left.size + right.size - shared) >= .85;
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === "object") return Object.fromEntries(Object.keys(value).sort().map((key) => [key, canonical(value[key])]));
  return value;
}

export const digestJson = (value) => createHash("sha256").update(JSON.stringify(canonical(value))).digest("hex");

function keyOf(item, branch) {
  if (branch === "relation") return JSON.stringify([item.leftId, item.rightId, item.kind]);
  return JSON.stringify([item.kind, item.evidence ?? ""]);
}

function validExpected(item, sourceText, branch) {
  if (!item || typeof item !== "object" || !nonempty(item.kind)) return false;
  if (branch === "assertion" && !assertionKinds.has(item.kind)) return false;
  if (branch === "relation" && (!nonempty(item.leftId) || !nonempty(item.rightId)
      || !["broader", "related"].includes(item.kind))) return false;
  if (item.kind === "none") return branch === "assertion" && item.evidence === "";
  return nonempty(item.evidence) && sourceText.includes(item.evidence);
}

function validRelationEndpoints(item) {
  if (item.branch !== "relation") return true;
  const endpoints = item.endpointConcepts;
  return Array.isArray(endpoints) && endpoints.length === 2
    && new Set(endpoints.map((entry) => entry?.id)).size === 2
    && endpoints.every((entry) => nonempty(entry?.id) && nonempty(entry?.label)
      && nonempty(entry?.evidence) && item.sourceText.includes(entry.evidence))
    && (item.expected ?? []).every((entry) => endpoints.some((endpoint) => endpoint.id === entry.leftId)
      && endpoints.some((endpoint) => endpoint.id === entry.rightId));
}

export function assessReadiness(manifest, policy) {
  const reasons = [];
  const cases = Array.isArray(manifest?.cases) ? manifest.cases : [];
  if (manifest?.formatVersion !== 2 || !nonempty(manifest?.datasetId) || !cases.length) code(reasons, "invalid-corpus");
  if (!policy?.coverage || !policy?.thresholds || !nonempty(policy?.id)) code(reasons, "missing-frozen-policy");
  const ids = new Set(); const familySplits = new Map(); const duplicateSplits = new Map();
  const priorTexts = [];
  const counts = Object.fromEntries(BRANCHES.map((branch) => [branch, { cases: 0, positives: 0, negatives: 0, random: 0, kinds: {} }]));
  let eligibleCases = 0; let explicitMisses = 0; let conceptRelation = 0; let negativeRelations = 0;
  const observedPatterns = new Set();
  for (const item of cases) {
    if (!item || !nonempty(item.id) || ids.has(item.id)) code(reasons, "duplicate-or-invalid-case-id");
    ids.add(item?.id);
    const branch = item?.branch;
    const hasDispute = item?.reviews?.some((action) => action.decision === "disputed");
    const independent = item?.reviews?.some((action) => action?.role === "independent"
      && (hasDispute ? action.decision === "disputed" : action.decision === "approved")
      && nonempty(action.actorId) && nonempty(action.at) && action.actorId !== item.modelId);
    const adjudicator = !hasDispute || item?.reviews?.some((action) => action.role === "adjudicator"
      && action.decision === "approved" && nonempty(action.actorId) && nonempty(action.at)
      && action.actorId !== item.modelId
      && !item.reviews.some((prior) => prior.role === "independent" && prior.actorId === action.actorId));
    const review = independent && adjudicator;
    if (!review || !["reviewed", "adjudicated"].includes(item?.annotationStatus)) code(reasons, "unreviewed-cases");
    if (item?.privacyReview?.status !== "passed" || !nonempty(item.privacyReview?.reviewerId)
        || !nonempty(item.privacyReview?.reviewedAt)) code(reasons, "privacy-review-incomplete");
    if (hasDispute && item.annotationStatus !== "adjudicated") {
      code(reasons, "dispute-not-adjudicated");
    }
    if (hasDispute && !adjudicator) {
      code(reasons, "dispute-not-adjudicated");
    }
    if (!BRANCHES.includes(branch) || !SPLITS.has(item?.split) || !nonempty(item?.sourceText)
        || !nonempty(item?.owner) || !nonempty(item?.sourceId) || !nonempty(item?.semanticFamilyId)
        || !Array.isArray(item?.expected) || !item.expected.every((entry) => validExpected(entry, item.sourceText, branch))
        || !validRelationEndpoints(item)) {
      code(reasons, "invalid-case"); continue;
    }
    const priorFamily = familySplits.get(item.semanticFamilyId);
    if (priorFamily && priorFamily !== item.split) code(reasons, "semantic-family-crosses-splits");
    familySplits.set(item.semanticFamilyId, item.split);
    const duplicateKey = normalize(item.sourceText);
    const priorDuplicate = duplicateSplits.get(duplicateKey);
    if (priorDuplicate && priorDuplicate !== item.split) code(reasons, "normalized-duplicate-crosses-splits");
    duplicateSplits.set(duplicateKey, item.split);
    const tokens = words(item.sourceText);
    if (priorTexts.some((prior) => prior.split !== item.split && nearDuplicate(prior.tokens, tokens))) {
      code(reasons, "near-duplicate-crosses-splits");
    }
    priorTexts.push({ split: item.split, tokens });
    if (review && item.privacyReview?.status === "passed" && ["reviewed", "adjudicated"].includes(item.annotationStatus)) eligibleCases += 1;
    const group = counts[branch]; group.cases += 1;
    if (item.sampleKind === "random") group.random += 1;
    if (nonempty(item.errorSlice)) observedPatterns.add(item.errorSlice);
    const positive = item.expected.some((entry) => entry.kind !== "none");
    if (positive) group.positives += 1; else group.negatives += 1;
    for (const entry of item.expected) {
      group.kinds[entry.kind] = (group.kinds[entry.kind] ?? 0) + 1;
      if (entry.origin === "miss") explicitMisses += 1;
    }
    if (branch === "concept" || branch === "relation") conceptRelation += 1;
    if (branch === "relation" && !positive) negativeRelations += 1;
  }
  const coverage = policy?.coverage;
  if (coverage) {
    for (const kind of coverage.journalKinds ?? []) {
      if ((counts.journal.kinds[kind] ?? 0) < coverage.minPositivePerKind) code(reasons, "journal-kind-coverage");
    }
    for (const kind of coverage.assertionKinds ?? []) {
      if ((counts.assertion.kinds[kind] ?? 0) < coverage.minPositivePerKind) code(reasons, "assertion-kind-coverage");
    }
    for (const branch of BRANCHES) {
      if (counts[branch].negatives < coverage.minNegativePerBranch) code(reasons, "negative-coverage");
      if (counts[branch].random < coverage.minRandomPerBranch) code(reasons, "random-sample-coverage");
    }
    if (conceptRelation < coverage.minConceptRelation) code(reasons, "concept-relation-coverage");
    if (negativeRelations < coverage.minNegativeRelations) code(reasons, "negative-relation-coverage");
    for (const pattern of coverage.difficultPatterns ?? []) {
      if (!observedPatterns.has(pattern)) code(reasons, "difficult-pattern-coverage");
    }
  }
  return { status: reasons.length ? "open" : "ready", reasons, cases: cases.length,
    eligibleCases, explicitMisses, branches: counts, semanticFamilies: familySplits.size };
}

export function selectPartition(cases, partition, owner) {
  if (!SPLITS.has(partition) || !nonempty(owner)) throw new Error("frozen partition and owner required");
  const selected = cases.filter((item) => item.split === partition);
  if (!selected.length || selected.some((item) => item.owner !== owner)) throw new Error("partition or owner mismatch");
  return selected;
}

function branchScore(branch) {
  return { cases: 0, expected: 0, predicted: 0, truePositives: 0, falseAdditions: 0,
    misses: 0, excerptErrors: 0, unsupportedSkillProfile: 0, wrongPublishedRelations: 0,
    crossOwnerDisclosures: 0, runtimeErrors: 0, confusion: {}, classCounts: {},
    validExcerpts: 0, excerptChecks: 0, wrongDirection: 0, wrongKind: 0,
    unsupportedMeaning: 0 };
}

function addConfusion(report, expected, predicted) {
  report.confusion[expected] ??= {};
  report.confusion[expected][predicted] = (report.confusion[expected][predicted] ?? 0) + 1;
  for (const kind of [expected, predicted]) report.classCounts[kind] ??= { tp: 0, fp: 0, fn: 0 };
  if (expected === predicted) report.classCounts[expected].tp += 1;
  else { report.classCounts[expected].fn += 1; report.classCounts[predicted].fp += 1; }
}

export function scoreBranches(cases, observations, policy) {
  if (!Array.isArray(cases) || !Array.isArray(observations) || cases.length !== observations.length) throw new Error("observation count mismatch");
  const branches = Object.fromEntries(BRANCHES.map((branch) => [branch, branchScore(branch)]));
  const rows = [];
  for (let index = 0; index < cases.length; index += 1) {
    const item = cases[index]; const output = observations[index];
    if (!BRANCHES.includes(item.branch) || output?.caseId !== item.id || output?.branch !== item.branch) throw new Error("observation identity mismatch");
    const score = branches[item.branch]; score.cases += 1;
    if (output.status !== "completed" || !Array.isArray(output.predictions)) { score.runtimeErrors += 1; rows.push({ caseId: item.id, status: "runtime_error" }); continue; }
    if (output.owner !== item.owner || output.predictions.some((prediction) => prediction.owner && prediction.owner !== item.owner)) score.crossOwnerDisclosures += 1;
    const expected = item.expected ?? []; const actual = output.predictions; const used = new Set();
    score.expected += expected.length; score.predicted += actual.length;
    for (const prediction of actual) {
      if (prediction.kind !== "none") {
        score.excerptChecks += 1;
        if (nonempty(prediction.evidence) && item.sourceText.includes(prediction.evidence)) score.validExcerpts += 1;
        else score.excerptErrors += 1;
      }
      const match = expected.findIndex((candidate, at) => !used.has(at) && keyOf(candidate, item.branch) === keyOf(prediction, item.branch));
      if (match >= 0) {
        used.add(match); score.truePositives += 1;
        addConfusion(score, expected[match].kind, prediction.kind);
        rows.push({ caseId: item.id, status: "matched", prediction });
      } else {
        score.falseAdditions += 1;
        const sameEvidence = expected.find((candidate) => candidate.evidence === prediction.evidence && candidate.kind !== "none");
        addConfusion(score, sameEvidence?.kind ?? "none", prediction.kind);
        if (item.branch === "relation") {
          if (expected.some((candidate) => candidate.leftId === prediction.rightId
              && candidate.rightId === prediction.leftId && candidate.kind === prediction.kind)) score.wrongDirection += 1;
          else if (expected.some((candidate) => candidate.leftId === prediction.leftId
              && candidate.rightId === prediction.rightId && candidate.kind !== prediction.kind)) score.wrongKind += 1;
          if (nonempty(prediction.evidence) && item.sourceText.includes(prediction.evidence)
              && !expected.some((candidate) => keyOf(candidate, "relation") === keyOf(prediction, "relation"))) {
            score.unsupportedMeaning += 1;
          }
        }
        if (["skill_evidence", "profile_update"].includes(prediction.kind) && !expected.some((candidate) => candidate.kind === prediction.kind)) score.unsupportedSkillProfile += 1;
        if (item.branch === "relation" && prediction.published === true) score.wrongPublishedRelations += 1;
        rows.push({ caseId: item.id, status: "false_addition", prediction });
      }
    }
    for (let at = 0; at < expected.length; at += 1) {
      if (!used.has(at)) {
        score.misses += 1;
        if (!actual.some((candidate) => candidate.evidence === expected[at].evidence && candidate.kind !== "none")) addConfusion(score, expected[at].kind, "none");
        rows.push({ caseId: item.id, status: "miss", expected: expected[at] });
      }
    }
  }
  for (const branch of BRANCHES) {
    const report = branches[branch];
    report.precision = ratio(report.truePositives, report.predicted);
    report.recall = ratio(report.truePositives, report.expected);
    report.exactEvidenceValidity = ratio(report.validExcerpts, report.excerptChecks);
    report.perKindF1 = Object.fromEntries(Object.entries(report.classCounts).map(([kind, { tp, fp, fn }]) =>
      [kind, ratio(2 * tp, 2 * tp + fp + fn)]));
    const values = Object.values(report.perKindF1).filter((value) => value !== null);
    report.macroF1 = values.length ? values.reduce((total, value) => total + value, 0) / values.length : null;
  }
  return { branches, rows, policyId: policy?.id ?? null };
}

export function freezeRun({ policy, corpus, baselineId, candidateId, rollbackId, modelId, promptId, taxonomyId, scorerId,
                            runId, owner, partition, classifierId, classifierThreshold,
                            workerDigest, orcAdapterDigest }) {
  if (![policy?.id, corpus?.datasetId, baselineId, candidateId, rollbackId, modelId, promptId, taxonomyId, scorerId,
        runId, owner, classifierId, workerDigest, orcAdapterDigest].every(nonempty)
      || ![baselineId, candidateId, rollbackId, runId].every(safeReference)
      || !Number.isFinite(classifierThreshold) || classifierThreshold < 0 || classifierThreshold > 1
      || !SPLITS.has(partition)) {
    throw new Error("complete frozen configuration required");
  }
  return Object.freeze({ policyId: policy.id, policyDigest: digestJson(policy), corpusId: corpus.datasetId,
    corpusDigest: digestJson(corpus), baselineId, candidateId, rollbackId, modelId, promptId,
    taxonomyId, scorerId, runId, owner, partition, classifierId, classifierThreshold,
    workerDigest, orcAdapterDigest,
    configDigest: digestJson({ baselineId, candidateId, modelId, promptId, taxonomyId, scorerId,
      runId, owner, partition, classifierId, classifierThreshold, workerDigest, orcAdapterDigest }) });
}

async function assertPrivatePath(path) {
  const resolved = resolve(path); const parent = await realpath(dirname(resolved));
  if (parent === REPO_ROOT || parent.startsWith(`${REPO_ROOT}${sep}`)) throw new Error("private report must be outside repository");
  return join(parent, resolved.split(sep).at(-1));
}

export async function consumeHoldout(privateDir, run) {
  if (run.partition !== "locked_holdout") throw new Error("only locked holdout runs can consume a holdout");
  const target = await assertPrivatePath(join(privateDir, `holdout-${run.corpusDigest}.json`));
  let handle;
  try { handle = await open(target, "wx", 0o600); }
  catch (error) { if (error?.code === "EEXIST") throw new Error("holdout already consumed"); throw error; }
  try { await handle.writeFile(`${JSON.stringify({ corpusDigest: run.corpusDigest, candidateId: run.candidateId,
    configDigest: run.configDigest, policyDigest: run.policyDigest })}\n`); }
  finally { await handle.close(); }
  return { status: "consumed" };
}

export async function writePrivateReport(path, report) {
  const target = await assertPrivatePath(path);
  const handle = await open(target, "wx", 0o600);
  try { await handle.writeFile(`${JSON.stringify(report, null, 2)}\n`); }
  finally { await handle.close(); }
}

export function publicSummary({ score, readiness, run }) {
  const branches = Object.fromEntries(BRANCHES.map((name) => {
    const item = score?.branches?.[name] ?? branchScore(name);
    if (readiness?.status !== "ready" || item.cases < 10) return [name, { suppressed: true }];
    return [name, { cases: item.cases, expected: item.expected, predicted: item.predicted,
      truePositives: item.truePositives, falseAdditions: item.falseAdditions, misses: item.misses,
      excerptErrors: item.excerptErrors, unsupportedSkillProfile: item.unsupportedSkillProfile,
      wrongPublishedRelations: item.wrongPublishedRelations, runtimeErrors: item.runtimeErrors,
      precision: item.precision ?? null, recall: item.recall ?? null, macroF1: item.macroF1 ?? null }];
  }));
  return { formatVersion: 1, policyId: run?.policyId ?? null, policyDigest: run?.policyDigest ?? null,
    configDigest: run?.configDigest ?? null, rollbackId: run?.rollbackId ?? null,
    readiness: { status: readiness?.status ?? "open", reasons: readiness?.reasons ?? [],
      cases: readiness?.cases ?? 0, eligibleCases: readiness?.eligibleCases ?? 0 }, branches };
}

export function qualificationStatus({ readiness, score, migration, deployment, rollbackId, policy }) {
  let modelQuality = "open";
  if (readiness?.status === "ready" && score && policy) {
    const t = policy.thresholds; const b = score.branches;
    const checks = [
      ...BRANCHES.map((name) => b[name].cases >= 10),
      ...BRANCHES.map((name) => b[name].runtimeErrors === 0 && b[name].crossOwnerDisclosures <= t.maxCrossOwner &&
        b[name].exactEvidenceValidity === t.exactEvidence),
      b.journal.precision >= t.journalPrecision, b.journal.recall >= t.journalRecall,
      b.assertion.macroF1 >= t.assertionMacroF1,
      ...Object.values(b.assertion.perKindF1).map((value) => value !== null && value >= t.assertionPerKindF1),
      b.assertion.unsupportedSkillProfile <= t.maxUnsupportedSkillProfile,
      b.concept.precision >= t.conceptPrecision, b.concept.recall >= t.conceptRecall,
      b.relation.precision >= t.relationPrecision, b.relation.recall >= t.relationRecall,
      b.relation.wrongPublishedRelations <= t.maxWrongPublishedRelations,
    ];
    modelQuality = checks.every(Boolean) ? "passed" : "failed";
  }
  return { modelQuality, privateMigration: migration, publicDeployment: deployment, rollbackId };
}
