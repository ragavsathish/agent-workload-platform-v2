import assert from "node:assert/strict";
import { mkdtemp, readFile, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import {
  assessReadiness, scoreBranches, freezeRun, consumeHoldout, selectPartition,
  writePrivateReport, publicSummary, qualificationStatus,
} from "./knowledge-quality-v2.mjs";

const at = "2026-11-08T00:00:00Z";
const policy = {
  id: "quality-policy-v1", coverage: { journalKinds: ["learning"], assertionKinds: ["none", "skill_evidence"],
    minPositivePerKind: 1, minNegativePerBranch: 1, minConceptRelation: 1,
    minNegativeRelations: 1, minRandomPerBranch: 1, difficultPatterns: ["question"] },
  thresholds: { exactEvidence: 1, maxCrossOwner: 0, maxUnsupportedSkillProfile: 0,
    maxWrongPublishedRelations: 0, journalPrecision: .9, journalRecall: .8,
    assertionMacroF1: .85, assertionPerKindF1: .75, conceptPrecision: .9,
    conceptRecall: .8, relationPrecision: .95, relationRecall: .8 },
};
const source = "I learned graph modeling.";
const review = [{ actorId: "reviewer-1", role: "independent", decision: "approved", at }];
const privacy = { status: "passed", reviewerId: "privacy-1", reviewedAt: at };
const caseOf = (overrides = {}) => ({ id: "case-1", owner: "owner-a", sourceId: "source-1",
  sourceText: source, branch: "journal", semanticFamilyId: "family-1", split: "development",
  sourceKind: "production", errorSlice: "question", sampleKind: "random",
  annotationStatus: "reviewed", privacyReview: privacy, reviews: review,
  expected: [{ kind: "learning", evidence: source, text: source }], ...overrides });
const corpus = (...cases) => ({ formatVersion: 2, datasetId: "private-v1", cases });
const observation = (item, predictions) => ({ caseId: item.id, branch: item.branch, owner: item.owner,
  status: "completed", predictions });

test("quality-001 pending draft cannot become gold", () => {
  const draft = caseOf({ annotationStatus: "pending", reviews: [], privacyReview: { status: "pending" } });
  const result = assessReadiness(corpus(draft), policy);
  assert.equal(result.status, "open");
  assert.ok(result.reasons.includes("unreviewed-cases"));
  assert.ok(result.reasons.includes("privacy-review-incomplete"));
  assert.equal(JSON.stringify(result).includes(source), false);
});

test("quality-002 independent review and explicit miss retain provenance without publication", () => {
  const item = caseOf({ expected: [{ kind: "learning", evidence: source, text: source, origin: "miss" }] });
  const result = assessReadiness(corpus(item), policy);
  assert.equal(result.eligibleCases, 1);
  assert.equal(result.explicitMisses, 1);
  assert.equal("publication" in item, false);
  const disputed = caseOf({ annotationStatus: "adjudicated",
    reviews: [{ actorId: "reviewer-1", role: "independent", decision: "disputed", at },
      { actorId: "adjudicator-1", role: "adjudicator", decision: "approved", at }] });
  assert.equal(assessReadiness(corpus(disputed), policy).eligibleCases, 1);
  assert.equal(assessReadiness(corpus({ ...disputed, reviews: disputed.reviews.slice(0, 1) }), policy).status, "open");
});

test("quality-003 family leakage and privacy pending refuse readiness", () => {
  const first = caseOf();
  const second = caseOf({ id: "case-2", sourceId: "source-2", split: "locked_holdout",
    privacyReview: { status: "pending" } });
  const result = assessReadiness(corpus(first, second), policy);
  assert.ok(result.reasons.includes("semantic-family-crosses-splits"));
  assert.ok(result.reasons.includes("privacy-review-incomplete"));
});

test("quality-004 branch scores are separate and expose denominators", () => {
  const journal = caseOf();
  const assertion = caseOf({ id: "case-2", sourceId: "source-2", branch: "assertion",
    semanticFamilyId: "family-2", expected: [{ kind: "none", evidence: "", text: "" }] });
  const relation = caseOf({ id: "case-3", sourceId: "source-3", branch: "relation",
    semanticFamilyId: "family-3", expected: [] });
  const score = scoreBranches([journal, assertion, relation], [
    observation(journal, journal.expected), observation(assertion, assertion.expected), observation(relation, []),
  ], policy);
  assert.equal(score.branches.journal.cases, 1);
  assert.equal(score.branches.assertion.cases, 1);
  assert.equal(score.branches.relation.cases, 1);
  assert.equal(score.branches.journal.truePositives, 1);
  assert.equal(score.branches.assertion.confusion.none.none, 1);
});

test("quality-005 exact quote does not establish a skill", () => {
  const question = "Can you explain graph modeling?";
  const item = caseOf({ branch: "assertion", sourceText: question, expected: [{ kind: "none", evidence: "", text: "" }] });
  const score = scoreBranches([item], [observation(item, [{ kind: "skill_evidence", evidence: question, text: "Graph modeling" }])], policy);
  assert.equal(score.branches.assertion.exactEvidenceValidity, 1);
  assert.equal(score.branches.assertion.unsupportedSkillProfile, 1);
  assert.equal(score.branches.assertion.falseAdditions, 1);
});

test("quality-006 private row report is restrictive and public summary excludes case material", async () => {
  const dir = await mkdtemp(join(tmpdir(), "awp-quality-test-"));
  const privatePath = join(dir, "private.json");
  const item = caseOf();
  const score = scoreBranches([item], [observation(item, [])], policy);
  await writePrivateReport(privatePath, { cases: [item], score });
  assert.equal((await stat(privatePath)).mode & 0o777, 0o600);
  assert.ok((await readFile(privatePath, "utf8")).includes(source));
  const summary = publicSummary({ score, readiness: assessReadiness(corpus(item), policy), run: { policyId: policy.id, rollbackId: "incumbent" } });
  assert.equal(JSON.stringify(summary).includes(source), false);
  assert.equal(JSON.stringify(summary).includes(item.id), false);
  assert.equal(JSON.stringify(summary).includes(dir), false);
});

test("quality-007 frozen run has immutable digests and holdout can be consumed once", async () => {
  const dir = await mkdtemp(join(tmpdir(), "awp-quality-holdout-"));
  const run = freezeRun({ policy, corpus: corpus(caseOf({ split: "locked_holdout" })),
    baselineId: "incumbent", candidateId: "candidate", rollbackId: "incumbent",
    modelId: "model", promptId: "prompt", taxonomyId: "personal-v1", scorerId: "scorer",
    runId: "synthetic-run", owner: "owner-a", partition: "locked_holdout",
    classifierId: "classifier", classifierThreshold: .6, workerDigest: "worker-digest",
    orcAdapterDigest: "orc-adapter-digest" });
  assert.equal(run.policyId, policy.id);
  assert.match(run.policyDigest, /^[a-f0-9]{64}$/);
  assert.equal((await consumeHoldout(dir, run)).status, "consumed");
  await assert.rejects(() => consumeHoldout(dir, run), /already consumed/);
  const selected = selectPartition([caseOf({ split: "development" }),
    caseOf({ id: "locked", split: "locked_holdout" })], "development", "owner-a");
  assert.deepEqual(selected.map((item) => item.id), ["case-1"]);
});

test("quality-008 evaluation labels do not issue owner knowledge commands", () => {
  const item = caseOf();
  const score = scoreBranches([item], [observation(item, item.expected)], policy);
  assert.equal(score.branches.journal.truePositives, 1);
  assert.equal("ownerCommands" in score, false);
});

test("quality-009 incomplete review keeps independent gates and rollback", () => {
  const readiness = assessReadiness(corpus(caseOf({ annotationStatus: "pending", reviews: [] })), policy);
  const status = qualificationStatus({ readiness, score: null, migration: "open", deployment: "open", rollbackId: "incumbent" });
  assert.deepEqual(status, { modelQuality: "open", privateMigration: "open", publicDeployment: "open", rollbackId: "incumbent" });
});

test("relation-011 reviewed endpoint pairs score direction, kind, and unsupported meaning", () => {
  const text = "Event modeling is a kind of software design.";
  const endpoints = [{ id: "event", label: "Event modeling", evidence: "Event modeling" },
    { id: "design", label: "software design", evidence: "software design" }];
  const item = caseOf({ branch: "relation", sourceText: text, endpointConcepts: endpoints,
    expected: [{ leftId: "event", rightId: "design", kind: "broader", evidence: text }] });
  const score = scoreBranches([item], [observation(item, [
    { leftId: "design", rightId: "event", kind: "broader", evidence: text, published: false },
    { leftId: "event", rightId: "design", kind: "related", evidence: text, published: false },
  ])], policy).branches.relation;
  assert.equal(score.wrongDirection, 1);
  assert.equal(score.wrongKind, 1);
  assert.equal(score.unsupportedMeaning, 2);
  assert.equal(score.exactEvidenceValidity, 1);
  assert.equal(score.wrongPublishedRelations, 0);
  assert.equal(score.misses, 1);
  assert.equal(assessReadiness(corpus({ ...item, endpointConcepts: [] }), policy).status, "open");
});

test("relation-012 protocol success without independent corpus remains open", () => {
  const text = "Event modeling is a kind of software design.";
  const item = caseOf({ branch: "relation", sourceText: text,
    endpointConcepts: [{ id: "event", label: "Event modeling", evidence: "Event modeling" },
      { id: "design", label: "software design", evidence: "software design" }],
    expected: [{ leftId: "event", rightId: "design", kind: "broader", evidence: text }],
    annotationStatus: "pending", reviews: [] });
  assert.ok(assessReadiness(corpus(item), policy).reasons.includes("unreviewed-cases"));
});
