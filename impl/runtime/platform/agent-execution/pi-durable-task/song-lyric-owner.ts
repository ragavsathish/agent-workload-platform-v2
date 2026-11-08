import { createHash } from "node:crypto";
import { realpath, mkdir } from "node:fs/promises";
import { dirname } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { Effect } from "effect";
import { runPiSession } from "./session-execution.js";

type Manifest = { title: string; style: string; lyrics: string; duration_seconds: number;
  bpm: number; key_scale: string; time_signature: string };
type Work = { taskId: string; workspace: string; instruction: string; signal: AbortSignal };
type Input = { kind: "song-lyrics"; requestId: string; params: {
  generation_id: string; identity: string; brief: string; workspace: string;
  style: string; duration_seconds: number;
} };
type Row = { task_id: string; input_json: string; input_digest: string; state: string;
  result_json: string | null; failure: string | null; attempt: number };
export type SongLyricOwnerOptions = { storagePath: string; workspaceRoots: Record<string, string>;
  runSession?: (work: Work) => Promise<string> };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u;
const SECTION = /\[(?:Verse(?: [1-9][0-9]*)?|Pre-Chorus|Chorus|Bridge|Outro)\]/iu;
const KEY = /^[A-G](?:#|b)? (?:Major|Minor)$/u;
const digest = (value: string) => createHash("sha256").update(value).digest("hex");
const object = (value: unknown): value is Record<string, unknown> =>
  !!value && typeof value === "object" && !Array.isArray(value);
const bounded = (value: unknown, maximum: number): value is string =>
  typeof value === "string" && value.trim().length > 0 && value.trim().length <= maximum;

function validate(input: unknown, taskId: string, roots: Record<string, string>): Input {
  if (!UUID.test(taskId) || !object(input) || input.kind !== "song-lyrics" || input.requestId !== taskId
      || !object(input.params)) throw new Error("invalid lyric task input");
  const params = input.params;
  if (!bounded(params.generation_id, 128) || !bounded(params.identity, 256)
      || !bounded(params.brief, 20_000) || !bounded(params.style, 1_000)
      || typeof params.workspace !== "string" || !Object.hasOwn(roots, params.workspace)
      || !Number.isInteger(params.duration_seconds) || Number(params.duration_seconds) < 30
      || Number(params.duration_seconds) > 300) throw new Error("invalid lyric task parameters");
  return input as Input;
}

export function lyricInstruction(input: Input): string {
  const { brief, style, duration_seconds } = input.params;
  return `Write original song lyrics grounded only in this task context:\n\n${brief.trim()}\n\n`
    + `Requested musical direction: ${style.trim()}\nTarget duration: ${duration_seconds} seconds.\n\n`
    + "Inspect the workspace when useful. Do not include secrets, credentials, private identity, "
    + "unreviewed personal information, or invented implementation claims. Use section markers "
    + "such as [Verse 1], [Pre-Chorus], [Chorus], [Bridge], and [Outro].\n\n"
    + "Return one JSON object with a presentation.songManifest object. Its exact fields are "
    + "title, style, lyricLines, durationSeconds, bpm, keyScale, and timeSignature. "
    + "lyricLines is an array of section markers and lyric lines. Use duration 30–300, BPM 40–200, "
    + "a key such as D Minor, and meter 3, 4, or 6. The response says lyrics are ready for review; "
    + "do not claim that audio exists. Return no markdown fence or extra prose.";
}

export function parseLyricResult(text: string): Manifest {
  let parsed: unknown;
  try { parsed = JSON.parse(text.trim()); }
  catch { throw new Error("lyric task did not return structured JSON"); }
  if (!object(parsed) || !object(parsed.presentation) || !object(parsed.presentation.songManifest)) {
    throw new Error("lyric task did not return a song manifest");
  }
  const raw = parsed.presentation.songManifest;
  if (!bounded(raw.title, 200) || !bounded(raw.style, 1_000)
      || !Array.isArray(raw.lyricLines) || raw.lyricLines.length === 0 || raw.lyricLines.length > 500
      || raw.lyricLines.some((line: unknown) => typeof line !== "string" || /[\r\n]/u.test(line))
      || !Number.isInteger(raw.durationSeconds) || Number(raw.durationSeconds) < 30
      || Number(raw.durationSeconds) > 300 || !Number.isInteger(raw.bpm)
      || Number(raw.bpm) < 40 || Number(raw.bpm) > 200
      || !bounded(raw.keyScale, 32) || !KEY.test(raw.keyScale)
      || !["3", "4", "6"].includes(String(raw.timeSignature))) {
    throw new Error("lyric task returned an invalid song manifest");
  }
  const lyrics = raw.lyricLines.join("\n");
  if (!bounded(lyrics, 20_000) || !SECTION.test(lyrics)) {
    throw new Error("lyric task returned invalid sectioned lyrics");
  }
  return { title: raw.title.trim(), style: raw.style.trim(), lyrics,
    duration_seconds: Number(raw.durationSeconds), bpm: Number(raw.bpm),
    key_scale: raw.keyScale.trim(), time_signature: String(raw.timeSignature) };
}

export class SongLyricOwner {
  private readonly active = new Map<string, { promise: Promise<void>; abort: AbortController }>();
  private closed = false;
  constructor(private readonly db: DatabaseSync, private readonly roots: Record<string, string>,
    private readonly runSession: (work: Work) => Promise<string>) {}

  private row(id: string): Row | undefined {
    return this.db.prepare("SELECT * FROM lyric_tasks WHERE task_id = ?").get(id) as Row | undefined;
  }
  async submit(taskId: string, candidate: unknown): Promise<{ task_id: string }> {
    if (this.closed) throw new Error("lyric owner is closed");
    const input = validate(candidate, taskId, this.roots);
    const inputJson = JSON.stringify(input);
    const inputDigest = digest(inputJson);
    const existing = this.row(taskId);
    if (existing) {
      if (existing.input_digest !== inputDigest) throw new Error("lyric task identity conflict");
      return { task_id: taskId };
    }
    this.db.prepare("INSERT INTO lyric_tasks(task_id,input_json,input_digest,state,attempt) VALUES (?,?,?,?,0)")
      .run(taskId, inputJson, inputDigest, "pending");
    this.launch(taskId, input);
    return { task_id: taskId };
  }
  private launch(taskId: string, input: Input): void {
    if (this.active.has(taskId)) return;
    const abort = new AbortController();
    const promise = Effect.runPromise(Effect.tryPromise(async () => {
      const row = this.row(taskId);
      if (!row || row.state !== "pending") return;
      this.db.prepare("UPDATE lyric_tasks SET state='running', attempt=attempt+1 WHERE task_id=? AND state='pending'").run(taskId);
      const workspace = await realpath(this.roots[input.params.workspace]);
      const text = await this.runSession({ taskId, workspace, instruction: lyricInstruction(input), signal: abort.signal });
      const manifest = parseLyricResult(text);
      const result = { task_id: taskId, generation_id: input.params.generation_id,
        identity: input.params.identity, manifest };
      this.db.prepare("UPDATE lyric_tasks SET state='completed',result_json=?,failure=NULL WHERE task_id=? AND state='running'")
        .run(JSON.stringify(result), taskId);
    })).catch((error: unknown) => {
      this.db.prepare("UPDATE lyric_tasks SET state='failed',failure=? WHERE task_id=? AND state='running'")
        .run("lyric generation failed", taskId);
      void error;
    }).finally(() => { this.active.delete(taskId); });
    this.active.set(taskId, { promise, abort });
  }
  async settle(taskId: string): Promise<void> { await this.active.get(taskId)?.promise; }
  async get(taskId: string): Promise<object | null> {
    const row = this.row(taskId);
    return row ? { task_id: taskId, state: row.state, result: row.result_json ? JSON.parse(row.result_json) : null,
      failure: row.failure, attempt: row.attempt } : null;
  }
  async retry(taskId: string): Promise<void> {
    const row = this.row(taskId);
    if (!row || row.state !== "uncertain") throw new Error("lyric task is not uncertain");
    this.db.prepare("UPDATE lyric_tasks SET state='pending',failure=NULL WHERE task_id=? AND state='uncertain'").run(taskId);
    this.launch(taskId, JSON.parse(row.input_json) as Input);
  }
  async cancel(taskId: string, owner: string): Promise<void> {
    const row = this.row(taskId);
    if (!row) throw new Error("lyric task not found");
    const input = JSON.parse(row.input_json) as Input;
    if (input.params.identity !== owner) throw new Error("lyric task not found");
    this.db.prepare("UPDATE lyric_tasks SET state='cancelled' WHERE task_id=? AND state IN ('pending','running','uncertain')")
      .run(taskId);
    this.active.get(taskId)?.abort.abort();
  }
  async close(): Promise<void> {
    if (this.closed) return;
    this.closed = true;
    for (const [taskId, active] of this.active) {
      this.db.prepare("UPDATE lyric_tasks SET state='uncertain',failure='interrupted attempt requires explicit recovery' WHERE task_id=? AND state='running'")
        .run(taskId);
      active.abort.abort();
    }
    await Promise.allSettled([...this.active.values()].map((active) => active.promise));
    this.db.close();
  }
}

export async function openSongLyricOwner(options: SongLyricOwnerOptions): Promise<SongLyricOwner> {
  await mkdir(dirname(options.storagePath), { recursive: true, mode: 0o700 });
  const db = new DatabaseSync(options.storagePath);
  db.exec("PRAGMA journal_mode=WAL; CREATE TABLE IF NOT EXISTS lyric_tasks (task_id TEXT PRIMARY KEY, input_json TEXT NOT NULL, input_digest TEXT NOT NULL, state TEXT NOT NULL, result_json TEXT, failure TEXT, attempt INTEGER NOT NULL)");
  db.exec("UPDATE lyric_tasks SET state='uncertain',failure='interrupted attempt requires explicit recovery' WHERE state='running'");
  return new SongLyricOwner(db, options.workspaceRoots,
    options.runSession ?? ((work) => runPiSession(work)));
}
