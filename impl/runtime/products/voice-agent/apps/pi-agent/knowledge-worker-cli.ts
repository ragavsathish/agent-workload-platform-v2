import { Effect } from "effect";
import { defaultServices, processWork, type Work } from "./knowledge-worker.js";

async function input(): Promise<Work> {
  let value = "";
  process.stdin.setEncoding("utf8");
  for await (const chunk of process.stdin) value += chunk;
  return JSON.parse(value) as Work;
}
function endpoint() {
  const base = process.env.AWP_GRAIN_SESSION_URL;
  const token = process.env.AWP_GRAIN_SESSION_TOKEN;
  if (!base || !token) throw new Error("Grain worker endpoint and token are required");
  const url = new URL(base);
  if (url.protocol !== "http:" || !["127.0.0.1", "localhost"].includes(url.hostname))
    throw new Error("Grain worker endpoint must be loopback HTTP");
  return { base: base.replace(/\/$/u, ""), token };
}
async function post(work: Work, path: string, body: object) {
  const { base, token } = endpoint();
  const response = await fetch(`${base}/internal/knowledge/${path}`, {
    method: "POST",
    headers: { authorization: `Bearer ${token}`, "x-awp-owner": work.owner, "content-type": "application/json" },
    body: JSON.stringify(body), signal: AbortSignal.timeout(10_000),
  });
  const reply = await response.json() as Record<string, unknown>;
  if (!response.ok || reply.error) throw new Error(`knowledge result refused: ${String(reply.error ?? response.status)}`);
  return reply;
}
const work = await input();
try {
  const result = await Effect.runPromise(processWork(work, defaultServices()));
  const body = work.mode === "daily-account" ? {
    "account-id": result.accountId, "task-id": result.taskId, "result-id": result.resultId,
    title: result.title, story: result.story, sources: result.sources?.map((source) => ({
      "source-ref": source.sourceRef, speaker: source.speaker, "captured-at": source.capturedAt,
      transcript: source.transcript,
    })), model: result.model, "processor-version": result.processorVersion,
  } : {
    "source-id": result.sourceId, "task-id": result.taskId, "result-id": result.resultId,
    mode: result.mode, status: result.status, reason: undefined,
    suggestions: result.suggestions?.map((item) => ({ "item-id": item.itemId, kind: item.kind, text: item.text, evidence: item.evidence, "entry-type": item.entryType })),
    model: result.model, "processor-version": result.processorVersion,
    classification: "classification" in result ? result.classification : undefined,
  };
  if (process.argv.includes("--emit-result")) {
    process.stdout.write(`${JSON.stringify(body)}\n`);
  } else {
    const reply = await post(work, work.mode === "daily-account" ? "daily-worker-result" : "worker-result", body);
    process.stdout.write(`${JSON.stringify({ taskId: work.taskId, status: "incorporated", reply })}\n`);
  }
} catch (error) {
  const reason = error instanceof Error ? error.message.slice(0, 500) : "worker failed";
  const resultId = `${work.taskId}:failed`;
  try {
    if (process.argv.includes("--emit-result")) throw new Error("emit-result failure");
    if (work.mode === "daily-account") {
      await post(work, "daily-worker-result", {
        "account-id": work.accountId, "task-id": work.taskId, "result-id": resultId,
        reason, sources: work.sources?.map((source) => ({ "source-ref": source.sourceRef,
          speaker: source.speaker, "captured-at": source.capturedAt, transcript: source.transcript })),
        model: process.env.DIGITAL_TWIN_MODEL ?? "qwen3.5:9b-mlx", "processor-version": "knowledge-v2-1",
      });
    } else {
      await post(work, "worker-result", {
        "source-id": work.sourceId, "task-id": work.taskId, "result-id": resultId,
        mode: work.mode, status: "failed", reason, suggestions: [],
        model: process.env.DIGITAL_TWIN_MODEL ?? "qwen3.5:9b-mlx", "processor-version": "knowledge-v2-1",
      });
    }
  } catch (reportError) {
    process.stderr.write(`knowledge failure could not be reported: ${String(reportError)}\n`);
  }
  process.stderr.write(`knowledge worker failed: ${reason}\n`);
  process.exitCode = 1;
}
