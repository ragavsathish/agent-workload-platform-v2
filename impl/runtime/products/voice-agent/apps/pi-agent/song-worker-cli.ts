import { Effect } from "effect";
import { encodeM4a, localStore, renderSong, type RenderWork } from "./song-worker.js";

const raw = await new Promise<string>((resolve, reject) => {
  let input = "";
  process.stdin.setEncoding("utf8");
  process.stdin.on("data", (chunk: string) => { input += chunk; if (input.length > 100_000) reject(new Error("work too large")); });
  process.stdin.on("end", () => resolve(input));
});
const work = JSON.parse(raw) as RenderWork;
const dir = process.env.AWP_SONG_ARTIFACT_DIR;
if (!dir) throw new Error("AWP_SONG_ARTIFACT_DIR is required");
const result = await Effect.runPromise(renderSong(work, {
  baseUrl: (process.env.AWP_ACE_STEP_URL ?? "http://127.0.0.1:8001").replace(/\/$/u, ""),
  fetch, timeoutMs: Number(process.env.AWP_ACE_STEP_TIMEOUT_MS ?? 900_000),
  maxBytes: 128 * 1024 * 1024,
  encode: (wav) => encodeM4a(wav, process.env.AWP_FFMPEG ?? "ffmpeg"),
  store: localStore(dir),
}));
process.stdout.write(`${JSON.stringify(result)}\n`);
