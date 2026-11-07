import { Effect } from "effect";
import { execFile } from "node:child_process";
import { mkdtemp, readFile, rename, rm, writeFile, mkdir } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { promisify } from "node:util";

export type Manifest = {
  title: string; style: string; lyrics: string; durationSeconds: number;
  bpm: number; keyScale: string; timeSignature: "3" | "4" | "6";
};
export type RenderWork = { generationId: string; taskId: string; owner: string; manifest: Manifest };
export type RenderServices = {
  baseUrl: string; fetch: typeof fetch; timeoutMs: number; maxBytes: number;
  encode: (wav: Uint8Array) => Promise<Uint8Array>;
  store: (generationId: string, audio: Uint8Array) => Promise<string>;
};
const wait = (ms: number) => Effect.sleep(ms);
const requestJson = (services: RenderServices, path: string, body: unknown) => Effect.tryPromise({
  try: async () => {
    const response = await services.fetch(`${services.baseUrl}${path}`, {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify(body), signal: AbortSignal.timeout(15_000),
    });
    if (!response.ok) throw new Error("music service request failed");
    const value = await response.json() as Record<string, unknown>;
    if (value.code !== 200) throw new Error("music service rejected request");
    return value;
  }, catch: (error) => new Error(String(error)),
});
const manifestValid = (m: Manifest) => m && typeof m.title === "string" && m.title.trim().length > 0 && m.title.length <= 200
  && typeof m.style === "string" && m.style.trim().length > 0 && m.style.length <= 1000
  && typeof m.lyrics === "string" && m.lyrics.length > 0 && m.lyrics.length <= 20_000
  && /\[(?:Verse(?: [1-9][0-9]*)?|Pre-Chorus|Chorus|Bridge|Outro)\]/iu.test(m.lyrics)
  && Number.isInteger(m.durationSeconds) && m.durationSeconds >= 30 && m.durationSeconds <= 300
  && Number.isInteger(m.bpm) && m.bpm >= 40 && m.bpm <= 200
  && /^[A-G](?:#|b)? (?:Major|Minor)$/u.test(m.keyScale)
  && ["3", "4", "6"].includes(m.timeSignature);
export const requestPayload = (m: Manifest) => ({
  prompt: m.style, lyrics: m.lyrics, model: "acestep-v15-turbo", thinking: true,
  use_format: false, vocal_language: "en", audio_format: "wav",
  audio_duration: m.durationSeconds, bpm: m.bpm, key_scale: m.keyScale,
  time_signature: m.timeSignature, inference_steps: 8, shift: 3, batch_size: 1,
});

export function renderSong(work: RenderWork, services: RenderServices) {
  return Effect.gen(function* () {
    const url = new URL(services.baseUrl);
    if (url.protocol !== "http:" || !["127.0.0.1", "localhost"].includes(url.hostname)
        || !work.owner || !work.generationId || !work.taskId || !manifestValid(work.manifest)
        || services.maxBytes <= 0 || services.timeoutMs < 30_000 || services.timeoutMs > 1_800_000)
      throw new Error("invalid song rendering input");
    const released = yield* requestJson(services, "/release_task", requestPayload(work.manifest));
    const taskId = (released.data as Record<string, unknown> | undefined)?.task_id;
    if (typeof taskId !== "string" || !taskId) throw new Error("music service returned no task ID");
    const deadline = Date.now() + services.timeoutMs;
    let audioPath: string | undefined;
    while (Date.now() < deadline) {
      const queried = yield* requestJson(services, "/query_result", { task_id_list: [taskId] });
      const row = Array.isArray(queried.data) ? queried.data[0] as Record<string, unknown> | undefined : undefined;
      const result = typeof row?.result === "string" ? JSON.parse(row.result) as unknown : [];
      const item = Array.isArray(result) ? result[0] as Record<string, unknown> | undefined : undefined;
      if (item?.status === 2 || row?.status === 2) throw new Error("music generation failed");
      if (item?.status === 1) {
        if (typeof item.file !== "string" || !item.file.startsWith("/v1/audio?"))
          throw new Error("music service returned invalid audio path");
        audioPath = item.file;
        break;
      }
      yield* wait(2_000);
    }
    if (!audioPath) throw new Error("music generation timed out");
    const response = yield* Effect.tryPromise(() => services.fetch(`${services.baseUrl}${audioPath!}`, {
      signal: AbortSignal.timeout(30_000),
    }));
    if (!response.ok) throw new Error("audio download failed");
    const wav = new Uint8Array(yield* Effect.tryPromise(() => response.arrayBuffer()));
    if (!wav.length || wav.length > services.maxBytes) throw new Error("audio size invalid");
    const encoded = yield* Effect.tryPromise(() => services.encode(wav));
    if (!encoded.length || encoded.length > services.maxBytes) throw new Error("encoded audio size invalid");
    const artifactId = yield* Effect.tryPromise(() => services.store(work.generationId, encoded));
    if (!artifactId) throw new Error("artifact store returned no reference");
    return { generationId: work.generationId, taskId: work.taskId, artifactId };
  });
}

export async function encodeM4a(wav: Uint8Array, ffmpeg = "ffmpeg"): Promise<Uint8Array> {
  const dir = await mkdtemp(join(tmpdir(), "awp-song-"));
  try {
    const input = join(dir, "song.wav"); const output = join(dir, "song.m4a");
    await writeFile(input, wav);
    await promisify(execFile)(ffmpeg, ["-y", "-v", "error", "-i", input, "-c:a", "aac",
      "-b:a", "256k", "-movflags", "+faststart", output], { timeout: 120_000 });
    return new Uint8Array(await readFile(output));
  } finally { await rm(dir, { recursive: true, force: true }); }
}
export function localStore(dir: string) {
  return async (generationId: string, audio: Uint8Array) => {
    if (!/^[0-9a-f-]{36}$/u.test(generationId)) throw new Error("invalid generation identity");
    await mkdir(dir, { recursive: true, mode: 0o700 });
    const path = join(dir, `${generationId}.m4a`);
    const temporary = join(dir, `${generationId}.${process.pid}.tmp`);
    await writeFile(temporary, audio, { mode: 0o600 });
    await rename(temporary, path);
    return generationId;
  };
}
