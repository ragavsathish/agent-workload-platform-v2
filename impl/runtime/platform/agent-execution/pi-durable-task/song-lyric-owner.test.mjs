import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, realpath, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { openSongLyricOwner } from "./dist/song-lyric-owner.js";

const manifest = { title: "Carry it home", style: "Warm acoustic", lyricLines: ["[Verse 1]", "Carry the work", "[Chorus]", "Carry it home"], durationSeconds: 30, bpm: 96, keyScale: "D Minor", timeSignature: "4" };
const input = (taskId, owner = "owner@example.test") => ({ kind: "song-lyrics", requestId: taskId, params: { generation_id: "generation-1", identity: owner, brief: "A song about durable work", workspace: "scratch", style: "Warm acoustic", duration_seconds: 30 } });

test("durable-song-001 claimed lyric task uses selected workspace and brief", async () => {
  const dir = await mkdtemp(join(tmpdir(), "awp-song-owner-"));
  const calls = [];
  const service = await openSongLyricOwner({ storagePath: join(dir, "owner.sqlite"), workspaceRoots: { scratch: dir }, runSession: async (work) => { calls.push(work); return JSON.stringify({ presentation: { songManifest: manifest } }); } });
  try {
    const id = "00000000-0000-4000-8000-000000000001";
    assert.equal((await service.submit(id, input(id))).task_id, id);
    await service.settle(id);
    assert.equal(calls.length, 1);
    assert.equal(calls[0].workspace, await realpath(dir));
    assert.match(calls[0].instruction, /durable work/);
    assert.equal((await service.get(id)).state, "completed");
  } finally { await service.close(); await rm(dir, { recursive: true, force: true }); }
});

test("durable-song-005 replay and restart preserve one effect", async () => {
  const dir = await mkdtemp(join(tmpdir(), "awp-song-owner-"));
  let calls = 0;
  const options = { storagePath: join(dir, "owner.sqlite"), workspaceRoots: { scratch: dir }, runSession: async () => { calls++; return JSON.stringify({ presentation: { songManifest: manifest } }); } };
  let service = await openSongLyricOwner(options);
  try {
    const id = "00000000-0000-4000-8000-000000000002";
    await service.submit(id, input(id));
    await service.settle(id);
    await service.submit(id, input(id));
    await service.close();
    service = await openSongLyricOwner(options);
    await service.submit(id, input(id));
    assert.equal(calls, 1);
    assert.equal((await service.get(id)).state, "completed");
    await assert.rejects(service.submit(id, input(id, "other@example.test")));
  } finally { await service.close(); await rm(dir, { recursive: true, force: true }); }
});

test("durable-song-006 unstructured lyric result fails without a manifest", async () => {
  const dir = await mkdtemp(join(tmpdir(), "awp-song-owner-"));
  const service = await openSongLyricOwner({ storagePath: join(dir, "owner.sqlite"), workspaceRoots: { scratch: dir }, runSession: async () => "Here are your lyrics." });
  try {
    const id = "00000000-0000-4000-8000-000000000003";
    await service.submit(id, input(id));
    await service.settle(id);
    const snapshot = await service.get(id);
    assert.equal(snapshot.state, "failed");
    assert.equal(snapshot.result, null);
    assert.equal(snapshot.failure, "lyric generation failed");
  } finally { await service.close(); await rm(dir, { recursive: true, force: true }); }
});

test("durable-song-007 interrupted task stays uncertain until explicit retry", async () => {
  const dir = await mkdtemp(join(tmpdir(), "awp-song-owner-"));
  const storagePath = join(dir, "owner.sqlite");
  let calls = 0;
  const options = { storagePath, workspaceRoots: { scratch: dir }, runSession: async () => { calls++; return JSON.stringify({ presentation: { songManifest: manifest } }); } };
  let service = await openSongLyricOwner(options);
  try {
    const id = "00000000-0000-4000-8000-000000000004";
    await service.submit(id, input(id));
    await service.settle(id);
    await service.close();
    const db = new DatabaseSync(storagePath);
    db.prepare("UPDATE lyric_tasks SET state='running',result_json=NULL WHERE task_id=?").run(id);
    db.close();
    service = await openSongLyricOwner(options);
    assert.equal((await service.get(id)).state, "uncertain");
    await service.submit(id, input(id));
    assert.equal(calls, 1);
    await service.retry(id);
    await service.settle(id);
    assert.equal(calls, 2);
    assert.equal((await service.get(id)).state, "completed");
  } finally { await service.close(); await rm(dir, { recursive: true, force: true }); }
});

test("durable-song-008 foreign cancellation is refused", async () => {
  const dir = await mkdtemp(join(tmpdir(), "awp-song-owner-"));
  const service = await openSongLyricOwner({ storagePath: join(dir, "owner.sqlite"), workspaceRoots: { scratch: dir }, runSession: async () => JSON.stringify({ presentation: { songManifest: manifest } }) });
  try {
    const id = "00000000-0000-4000-8000-000000000005";
    await service.submit(id, input(id));
    await service.settle(id);
    await assert.rejects(service.cancel(id, "other@example.test"));
    assert.equal((await service.get(id)).state, "completed");
  } finally { await service.close(); await rm(dir, { recursive: true, force: true }); }
});
