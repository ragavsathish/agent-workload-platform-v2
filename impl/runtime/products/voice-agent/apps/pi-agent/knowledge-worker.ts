import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { Effect } from "effect";

export type Source = { sourceRef: string; speaker: "owner"; capturedAt: string; transcript: string };
export type Work = {
  owner: string; sourceId?: string; accountId?: string; taskId: string;
  mode: "journal" | "classification" | "daily-account";
  transcript?: string; capturedAt?: string; journalDate?: string; timeZone?: string; sources?: Source[];
};
export type Suggestion = { itemId: string; kind: "journal" | "assertion"; text: string; evidence: string; entryType?: string };
export type Services = {
  modelBase: string; model: string; classifierBase: string; classifierModel: string;
  reviewThreshold: number; fetch: typeof fetch; promptDir: URL;
};
const kinds = new Set(["thought", "idea", "question", "decision", "goal", "learning", "unclassified"]);
const assertionKinds = new Set(["conversation_topic", "skill_evidence", "profile_update", "none"]);
const version = "knowledge-v2-1";
const hash = (value: string) => createHash("sha256").update(value).digest("hex").slice(0, 32);
const readPrompt = (services: Services, name: string) => Effect.tryPromise(() => readFile(new URL(name, services.promptDir), "utf8"));
const post = (services: Services, url: string, body: unknown) => Effect.tryPromise({
  try: async () => {
    const response = await services.fetch(url, {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify(body), signal: AbortSignal.timeout(300_000),
    });
    if (!response.ok) throw new Error(`model service returned ${response.status}`);
    return response.json() as Promise<Record<string, unknown>>;
  },
  catch: (error) => new Error(String(error)),
});
const parseContent = (value: Record<string, unknown>) => {
  const message = value.message as Record<string, unknown> | undefined;
  if (typeof message?.content !== "string") throw new Error("model returned no content");
  return JSON.parse(message.content) as Record<string, unknown>;
};
const clean = (value: string) => value.normalize("NFKC").toLocaleLowerCase("en-US").replace(/\s+/gu, " ").trim();
const lowValue = (value: string) => /^(?:check|run|open|show|please|okay|ok|thanks|thank you|system|assistant)\b/iu.test(value.trim())
  || value.trim().split(/\s+/u).length < 3;
const evidenceSupported = (text: string, evidence: string) => text.split(/\W+/u).some((word) => word.length > 3 && clean(evidence).includes(clean(word)));

export function processWork(work: Work, services: Services) {
  return Effect.gen(function* () {
    if (!work.owner || !work.taskId || !Number.isFinite(services.reviewThreshold)
        || services.reviewThreshold < 0 || services.reviewThreshold > 1) throw new Error("invalid worker identity or threshold");
    if (work.mode !== "daily-account" && (!work.sourceId || !work.transcript || work.transcript.length > 20_000))
      throw new Error("invalid source turn");
    const resultId = hash(`${work.owner}\0${work.taskId}\0${work.mode}\0${version}`);
    if (work.mode === "journal") {
      const sentences = work.transcript!.split(/(?<=[.!?])\s+|\n+/u).filter((sentence) => sentence.trim() && !lowValue(sentence));
      if (sentences.length === 0) return {
        resultId, taskId: work.taskId, sourceId: work.sourceId, mode: work.mode,
        status: "completed", suggestions: [], model: services.model, processorVersion: version,
        outcome: "filtered",
      };
      const prompt = yield* readPrompt(services, "journal.md");
      const raw = yield* post(services, `${services.modelBase}/api/chat`, {
        model: services.model, stream: false, think: false,
        format: { type: "object", additionalProperties: false, required: ["entries"], properties: {
          entries: { type: "array", maxItems: 12, items: { type: "object", additionalProperties: false,
            required: ["type", "text", "topics", "evidenceQuote"], properties: {
              type: { enum: [...kinds] }, text: { type: "string", minLength: 1, maxLength: 1000 },
              topics: { type: "array", maxItems: 8, items: { type: "string" } },
              evidenceQuote: { type: "string", minLength: 1, maxLength: 500 },
            } } },
        } },
        options: { temperature: 0 },
        messages: [{ role: "system", content: prompt },
          { role: "user", content: "I learned that retries need stable identifiers." },
          { role: "assistant", content: JSON.stringify({ entries: [{ type: "learning", text: "Retries need stable identifiers.", topics: ["retries"], evidenceQuote: "I learned that retries need stable identifiers." }] }) },
          { role: "user", content: "Check the logs." },
          { role: "assistant", content: JSON.stringify({ entries: [] }) },
          { role: "user", content: sentences.join("\n") }],
      });
      const parsed = parseContent(raw);
      if (!Array.isArray(parsed.entries) || parsed.entries.length > 12) throw new Error("invalid journal entries");
      const seen = new Set<string>();
      const suggestions: Suggestion[] = parsed.entries.flatMap((rawEntry, index) => {
        const entry = rawEntry as Record<string, unknown>;
        if (typeof entry.type !== "string" || !kinds.has(entry.type)
            || typeof entry.text !== "string" || !entry.text.trim() || entry.text.length > 1000
            || typeof entry.evidenceQuote !== "string" || !work.transcript!.includes(entry.evidenceQuote)
            || lowValue(entry.evidenceQuote) || !evidenceSupported(entry.text, entry.evidenceQuote)) return [];
        if (entry.type === "learning" && !/\b(?:learn|understand|realiz|realise|discover|know)\w*/iu.test(entry.evidenceQuote)) return [];
        if (entry.type === "decision" && !/\b(?:decid\w*|will)\b/iu.test(entry.evidenceQuote)) return [];
        const key = `${entry.type}:${clean(entry.text)}`;
        if (seen.has(key)) return [];
        seen.add(key);
        return [{ itemId: `${resultId}:entry:${index + 1}`, kind: "journal", text: entry.text.trim(), evidence: entry.evidenceQuote, entryType: entry.type }];
      });
      return { resultId, taskId: work.taskId, sourceId: work.sourceId, mode: work.mode,
        status: "completed", suggestions, model: services.model, processorVersion: version,
        outcome: suggestions.length ? "written" : "no-entries" };
    }
    if (work.mode === "classification") {
      const classified = yield* post(services, `${services.classifierBase}/v1/systemone`, {
        model: services.classifierModel, state: { speaker: "owner", transcript: work.transcript },
        questions: {
          assertion_kind: { type: "choice", instructions: "Choose one ontology bucket. Explicit owner skill evidence is skill_evidence. Do not infer personal facts from a question or command.", criteria: {
            conversation_topic: "Concrete subject discussed, not a durable owner fact",
            skill_evidence: "Explicit first-person skill or capability",
            profile_update: "Explicit biographical change",
            none: "Command, question, speculation, or insufficient evidence",
          } },
          profile_field: { type: "choice", instructions: "If this is a profile update, choose its field; otherwise choose none.", criteria: {
            job_title: "Current professional title", home_location: "Home location",
            credential: "Credential", employment: "Employment", none: "No explicit profile field",
          } },
        },
      });
      const answers = classified.answers as Record<string, Record<string, unknown>> | undefined;
      const kind = answers?.assertion_kind;
      const field = answers?.profile_field;
      if (typeof kind?.choice !== "string" || !assertionKinds.has(kind.choice)
          || typeof kind.confidence !== "number" || kind.confidence < 0 || kind.confidence > 1
          || typeof field?.choice !== "string" || typeof field.confidence !== "number") throw new Error("invalid classifier response");
      const classification = { kind: kind.choice, confidence: kind.confidence, profileField: field.choice,
        profileFieldConfidence: field.confidence, probabilities: kind.probabilities, classifierModel: services.classifierModel };
      if (kind.choice === "none" || kind.confidence < services.reviewThreshold
          || (kind.choice === "profile_update" && (field.choice === "none" || field.confidence < services.reviewThreshold))) {
        return { resultId, taskId: work.taskId, sourceId: work.sourceId, mode: work.mode,
          status: "completed", suggestions: [], classification, model: services.model, processorVersion: version,
          outcome: "no-candidate" };
      }
      const prompt = yield* readPrompt(services, "candidate.md");
      const extracted = parseContent(yield* post(services, `${services.modelBase}/api/chat`, {
        model: services.model, stream: false, think: false,
        format: { type: "object", additionalProperties: false, required: ["candidates"], properties: {
          candidates: { type: "array", maxItems: 8, items: { type: "object", additionalProperties: false,
            required: ["label", "evidence_quote"], properties: {
              label: { type: "string", minLength: 1, maxLength: 200 },
              evidence_quote: { type: "string", minLength: 1, maxLength: 1000 },
            } } },
        } },
        options: { temperature: 0 },
        messages: [{ role: "system", content: prompt }, { role: "user", content: work.transcript }],
      }));
      if (!Array.isArray(extracted.candidates) || extracted.candidates.length > 8) throw new Error("invalid candidate output");
      const suggestions: Suggestion[] = extracted.candidates.flatMap((rawCandidate, index) => {
        const candidate = rawCandidate as Record<string, unknown>;
        if (typeof candidate.label !== "string" || !candidate.label.trim() || candidate.label.length > 200
            || typeof candidate.evidence_quote !== "string" || !work.transcript!.includes(candidate.evidence_quote)) return [];
        return [{ itemId: `${resultId}:candidate:${index + 1}`, kind: "assertion", text: candidate.label.trim(), evidence: candidate.evidence_quote }];
      });
      return { resultId, taskId: work.taskId, sourceId: work.sourceId, mode: work.mode,
        status: "completed", suggestions, classification, model: services.model, processorVersion: version,
        outcome: suggestions.length ? "candidates" : "no-candidate" };
    }
    if (!work.accountId || !work.journalDate || !work.timeZone || !Array.isArray(work.sources)
        || work.sources.length < 1 || work.sources.length > 300) throw new Error("invalid daily work");
    const refs = new Set<string>();
    for (const source of work.sources) {
      if (source.speaker !== "owner" || !source.sourceRef || !source.transcript || source.transcript.length > 20_000
          || refs.has(source.sourceRef)) throw new Error("invalid daily source");
      refs.add(source.sourceRef);
      const parts = new Intl.DateTimeFormat("en-CA", { timeZone: work.timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date(source.capturedAt));
      const date = Object.fromEntries(parts.map((part) => [part.type, part.value]));
      if (`${date.year}-${date.month}-${date.day}` !== work.journalDate) throw new Error("daily source outside date");
    }
    if (work.sources.reduce((size, source) => size + source.transcript.length, 0) > 200_000) throw new Error("daily bundle too large");
    const prompt = yield* readPrompt(services, "daily.md");
    const generated = parseContent(yield* post(services, `${services.modelBase}/api/chat`, {
      model: services.model, stream: false, think: false,
      format: { type: "object", required: ["title", "story"], properties: { title: { type: "string" }, story: { type: "string" } } },
      options: { temperature: 0 },
      messages: [{ role: "system", content: prompt }, { role: "user", content: JSON.stringify({ journalDate: work.journalDate, timeZone: work.timeZone, sources: work.sources }) }],
    }));
    if (typeof generated.title !== "string" || !generated.title.trim() || generated.title.length > 200
        || typeof generated.story !== "string" || !generated.story.trim() || generated.story.length > 8000
        || /^(?:#{1,6}\s|[-*]\s|\d+[.)]\s)/mu.test(generated.story)) throw new Error("invalid daily prose");
    return { resultId, accountId: work.accountId, taskId: work.taskId, title: generated.title.trim(),
      story: generated.story.trim(), sources: work.sources, model: services.model, processorVersion: version };
  });
}

export const defaultServices = (): Services => ({
  // The source host often supplies an OpenAI-compatible /v1 endpoint. This
  // worker uses Ollama's native /api/chat path, so retain the origin only.
  modelBase: (process.env.DIGITAL_TWIN_MODEL_BASE_URL ?? "http://127.0.0.1:11434")
    .replace(/\/$/u, "").replace(/\/v1$/u, ""),
  model: process.env.DIGITAL_TWIN_MODEL ?? "qwen3.5:9b-mlx",
  classifierBase: (process.env.DIGITAL_TWIN_CLASSIFIER_BASE_URL ?? "http://127.0.0.1:8010").replace(/\/$/u, ""),
  classifierModel: process.env.DIGITAL_TWIN_CLASSIFIER_MODEL ?? "von-1.3.0",
  reviewThreshold: Number(process.env.DIGITAL_TWIN_REVIEW_THRESHOLD ?? "0.60"),
  fetch, promptDir: new URL("./knowledge/prompts/", import.meta.url),
});
