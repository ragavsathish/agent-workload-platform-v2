import assert from "node:assert/strict";
import { test } from "node:test";
import { Effect } from "effect";
import { renderSong, requestPayload } from "./dist/song-worker.js";

const manifest = { title: "Learning", style: "Warm acoustic", lyrics: "[Verse 1]\nLearn\n[Chorus]\nLearn again",
  durationSeconds: 30, bpm: 90, keyScale: "D Minor", timeSignature: "4" };
const work = { generationId: "00000000-0000-0000-0000-000000000001", taskId: "task-1", owner: "owner", manifest };
const response = (value) => ({ ok: true, json: async () => value, arrayBuffer: async () => new Uint8Array([1, 2, 3]).buffer });
test("song render uses approved manifest and stores only encoded audio", async () => {
  const requests = []; const stored = [];
  const services = { baseUrl: "http://127.0.0.1:8001", timeoutMs: 30_000, maxBytes: 10,
    fetch: async (url, options) => {
      requests.push([url, options?.body]);
      if (url.endsWith("/release_task")) return response({ code: 200, data: { task_id: "ace-task" } });
      if (url.endsWith("/query_result")) return response({ code: 200, data: [{ result: JSON.stringify([{ status: 1, file: "/v1/audio?task=x" }]) }] });
      return response({});
    },
    encode: async (wav) => { assert.deepEqual([...wav], [1, 2, 3]); return new Uint8Array([4, 5]); },
    store: async (id, audio) => { stored.push([id, [...audio]]); return "artifact-1"; },
  };
  const result = await Effect.runPromise(renderSong(work, services));
  assert.equal(result.artifactId, "artifact-1");
  assert.deepEqual(stored, [[work.generationId, [4, 5]]]);
  assert.equal(JSON.parse(requests[0][1]).lyrics, manifest.lyrics);
  assert.equal(requestPayload(manifest).audio_format, "wav");
});
test("song render refuses remote renderer and invalid audio path", async () => {
  const services = { baseUrl: "https://example.com", timeoutMs: 30_000, maxBytes: 10,
    fetch: async () => response({ code: 200, data: { task_id: "x" } }),
    encode: async () => new Uint8Array([1]), store: async () => "artifact" };
  await assert.rejects(Effect.runPromise(renderSong(work, services)));
  services.baseUrl = "http://127.0.0.1:8001";
  services.fetch = async (url) => url.endsWith("/release_task")
    ? response({ code: 200, data: { task_id: "x" } })
    : response({ code: 200, data: [{ result: JSON.stringify([{ status: 1, file: "file:///private" }]) }] });
  await assert.rejects(Effect.runPromise(renderSong(work, services)));
});
