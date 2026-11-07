import assert from "node:assert/strict";
import { spawn, execFileSync } from "node:child_process";
import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { createServer as createSocketServer } from "node:net";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import test from "node:test";

import { ModelRuntime, SessionManager } from "@earendil-works/pi-coding-agent";
import { VoiceAgent } from "./dist/voice-agent/voice-agent.js";
import { GrainSessionClient } from "./dist/voice-agent/grain-client.js";
import { PublicTranscript } from "./dist/voice-agent/public-transcript.js";
import {
  closeVoiceHttpServer, createVoiceHttpServer, listenVoiceHttpServer,
} from "./dist/voice-agent/voice-http-server.js";

const owner = "owner@example.test";
const serviceToken = "protocol-service-token";
const grainToken = "protocol-grain-token";
const implDir = resolve(import.meta.dirname, "../../../../..");

async function freePort() {
  const server = createServer();
  await new Promise((resolveListen) => server.listen(0, "127.0.0.1", resolveListen));
  const port = server.address().port;
  await new Promise((resolveClose) => server.close(resolveClose));
  return port;
}

async function waitForGrain(port, process) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (process.exitCode !== null) throw new Error("Grain bridge exited before readiness");
    try {
      const response = await fetch(`http://127.0.0.1:${port}/internal/session/catalog`, {
        method: "POST", headers: { authorization: `Bearer ${grainToken}`,
          "x-awp-owner": owner, "content-type": "application/json" }, body: "{}",
      });
      if (response.ok) return;
    } catch { /* service is still starting */ }
    await new Promise((resolveWait) => setTimeout(resolveWait, 100));
  }
  throw new Error("Grain bridge did not start");
}

test("live-001/002/004/005/006/007/008 Pi route and Grain process journey", async (t) => {
  const root = await mkdtemp(join(tmpdir(), "awp-v2-live-protocol-"));
  const socketDir = await mkdtemp("/tmp/awp-live-sock-");
  t.after(() => rm(socketDir, { recursive: true, force: true }));
  const agentDir = join(root, "agent");
  const sessionDir = join(root, "sessions");
  const cwd = join(root, "workspace");
  const skillsDir = join(root, "skills");
  const taskingDir = join(root, "tasking");
  await Promise.all([agentDir, sessionDir, cwd, skillsDir, taskingDir].map((path) =>
    mkdir(path)));
  const grainPort = await freePort();
  let modelCalls = 0;
  const modelServer = createServer(async (request, response) => {
    modelCalls += 1;
    let body = "";
    for await (const chunk of request) body += chunk;
    const messages = JSON.parse(body).messages;
    const user = [...messages].reverse().find((item) => item.role === "user");
    const spoken = Array.isArray(user?.content)
      ? user.content.map((part) => part.text ?? "").join("") : user?.content;
    if (spoken === "slow") {
      await new Promise((resolveDelay) => setTimeout(resolveDelay, 10000));
      if (response.destroyed) return;
    }
    const answer = `Pi heard ${spoken ?? "nothing"}`;
    response.writeHead(200, { "content-type": "text/event-stream" });
    response.write(`data: ${JSON.stringify({ id: "driver", object: "chat.completion.chunk",
      choices: [{ index: 0, delta: { role: "assistant", content: answer }, finish_reason: null }] })}\n\n`);
    response.write(`data: ${JSON.stringify({ id: "driver", object: "chat.completion.chunk",
      choices: [{ index: 0, delta: {}, finish_reason: "stop" }] })}\n\n`);
    response.end("data: [DONE]\n\n");
  });
  await new Promise((resolveListen) => modelServer.listen(0, "127.0.0.1", resolveListen));
  const modelPort = modelServer.address().port;
  t.after(() => modelServer.close());
  await writeFile(join(agentDir, "models.json"), JSON.stringify({ providers: { local: {
    baseUrl: `http://127.0.0.1:${modelPort}/v1`, api: "openai-completions",
    apiKey: "driver", models: [{ id: "driver", contextWindow: 16000, maxTokens: 2048 }],
  } } }));
  await writeFile(join(agentDir, "auth.json"), "{}");

  const taskId = "4b860912-01a3-455b-9fa4-7a71c93500ee";
  execFileSync("clojure", [
    "-J--add-opens=java.base/java.nio=ALL-UNNAMED",
    "-J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED", "-M", "-e",
    `(require '[awp-v2.tasking.api :as t]) (let [s (t/open-system {:storage-dir ${JSON.stringify(taskingDir)} :eligible-workspaces #{"project-a"}})] (t/submit! s ${JSON.stringify(owner)} {:request-id #uuid ${JSON.stringify(taskId)} :owner ${JSON.stringify(owner)} :instruction "Live task" :workspace "project-a"}) (t/dispatch! s #uuid ${JSON.stringify(taskId)} "project-a") (t/close-system! s))`,
  ], { cwd: implDir, stdio: "ignore" });
  const taskPrompts = [];
  const taskSocket = createSocketServer((socket) => {
    const send = (record) => socket.write(`${JSON.stringify(record)}\n`);
    send({ type: "remote_attached", taskId });
    let buffer = "";
    socket.on("data", (chunk) => {
      buffer += chunk.toString("utf8");
      while (buffer.includes("\n")) {
        const end = buffer.indexOf("\n");
        const record = JSON.parse(buffer.slice(0, end));
        buffer = buffer.slice(end + 1);
        if (record.type === "get_state") send({ type: "response", id: record.id,
          success: true, data: { isStreaming: false, messageCount: taskPrompts.length } });
        if (record.type === "prompt") {
          taskPrompts.push(record.message);
          send({ type: "response", id: record.id, success: true });
          send({ type: "message_update", assistantMessageEvent: {
            type: "text_delta", delta: "Task heard prompt" } });
          send({ type: "agent_settled" });
        }
        if (record.type === "get_last_assistant_text") send({ type: "response", id: record.id,
          success: true, data: { text: "Task heard prompt" } });
        if (record.type === "abort") send({ type: "response", id: record.id, success: true });
      }
    });
  });
  await new Promise((resolveListen) => taskSocket.listen(join(socketDir, `${taskId}.sock`), resolveListen));
  t.after(() => taskSocket.close());

  const startGrain = () => spawn("clojure", [
    "-J--add-opens=java.base/java.nio=ALL-UNNAMED",
    "-J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
    "-M", "-m", "awp-v2.live.main",
  ], { cwd: implDir, env: { ...process.env,
    AWP_SESSION_STORAGE_DIR: join(root, "grain-sessions"),
    AWP_TASKING_STORAGE_DIR: taskingDir,
    AWP_GRAIN_SESSION_TOKEN: grainToken,
    AWP_GRAIN_SESSION_PORT: String(grainPort),
  }, stdio: "ignore" });
  let grain = startGrain();
  t.after(() => grain.kill("SIGTERM"));
  await waitForGrain(grainPort, grain);
  const grainClient = new GrainSessionClient(`http://127.0.0.1:${grainPort}`, grainToken, owner);
  const selected = await grainClient.call("fresh", { "command-id": "protocol-fresh" });
  const runtime = await ModelRuntime.create({
    authPath: join(agentDir, "auth.json"), modelsPath: join(agentDir, "models.json"),
    modelsStorePath: join(agentDir, "models-store.json"), allowModelNetwork: false,
  });
  const settings = {
    agentDir, cwd, sessionDir, skillsDir, remoteTaskSocketDir: socketDir,
    model: { provider: "local", modelId: "driver", source: "local/driver" },
    thinking: "off", timeoutMs: 30000,
    grafanaMcpToken: "driver", grafanaMcpUrl: "http://127.0.0.1:9999/mcp",
    playwrightTraceArtifactRoot: root, playwrightTraceOutputRoot: root,
    playwrightTraceRenderer: "/bin/false",
    playwrightTraceViewerBaseUrl: "http://127.0.0.1:9999",
    wassetteBin: "/bin/false", wassetteComponentDir: root, wassetteEnvFile: root,
  };
  const manager = SessionManager.create(cwd, sessionDir, { id: selected.id });
  const toolEnvironment = { identity: "one-voice-vm", close: async () => {} };
  const agent = new VoiceAgent(settings, runtime, toolEnvironment, [], null, null,
    manager, new PublicTranscript(), grainClient);
  let agentClosed = false;
  t.after(() => agentClosed ? undefined : agent.close());
  const server = createVoiceHttpServer({ agent, ownerIdentity: owner, serviceToken,
    narrator: { summarize: async () => "" }, telemetry: { observe: (_name, operation) => operation() } });
  await listenVoiceHttpServer(server, 0, "127.0.0.1");
  t.after(() => closeVoiceHttpServer(server));
  const base = `http://127.0.0.1:${server.address().port}`;
  const call = async (method, path, body, headers = {}) => {
    const response = await fetch(`${base}${path}`, { method,
      headers: { authorization: `Bearer ${serviceToken}`, "x-awp-owner": owner,
        "content-type": "application/json", ...headers },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
    return { status: response.status, body: await response.json() };
  };
  const typed = await call("POST", "/v1/turn", { messages: [
    { role: "system", content: "Answer briefly." }, { role: "user", content: "typed" },
  ] }, { "x-awp-turn-id": "protocol-typed", "x-awp-origin": "typed" });
  assert.equal(typed.status, 200);
  assert.equal(typed.body.text, "Pi heard typed");
  const retry = await call("POST", "/v1/turn", { messages: [
    { role: "system", content: "Answer briefly." }, { role: "user", content: "typed" },
  ] }, { "x-awp-turn-id": "protocol-typed", "x-awp-origin": "typed" });
  assert.equal(retry.body.text, "Pi heard typed");
  assert.equal(modelCalls, 1);
  const uncertainId = "protocol-lost-answer";
  const uncertainMessages = [
    { role: "system", content: "Answer briefly." },
    { role: "user", content: "uncertain" },
  ];
  assert.equal((await grainClient.call("submit", {
    "turn-id": uncertainId, origin: "typed", messages: uncertainMessages,
  })).status, "accepted");
  await grainClient.call("settle", { "turn-id": uncertainId, status: "uncertain" });
  const uncertainRetry = await call("POST", "/v1/turn", { messages: uncertainMessages },
    { "x-awp-turn-id": uncertainId, "x-awp-origin": "typed" });
  assert.equal(uncertainRetry.status, 502);
  assert.match(uncertainRetry.body.detail, /uncertain/u);
  assert.equal(modelCalls, 1);
  const invalid = await call("POST", "/v1/turn", { messages: [
    { role: "system", content: "Answer briefly." },
    { role: "invalid", content: "never dispatch" },
  ] }, { "x-awp-turn-id": "protocol-invalid", "x-awp-origin": "typed" });
  assert.equal(invalid.status, 502);
  assert.equal(modelCalls, 1);
  const oversized = await call("POST", "/v1/turn", { messages: [
    { role: "system", content: "Answer briefly." },
    { role: "user", content: "x".repeat(132000) },
  ] }, { "x-awp-turn-id": "protocol-oversized", "x-awp-origin": "typed" });
  assert.equal(oversized.status, 502);
  assert.equal(modelCalls, 1);
  const context = await call("GET", "/v1/context");
  assert.equal(context.status, 200);
  assert.equal(context.body.canCompact, false);
  const prematureCompact = await call("POST", "/v1/context/compact", {});
  assert.equal(prematureCompact.status, 409);
  assert.equal(modelCalls, 1);
  const voiceResponse = await fetch(`${base}/v1/turn/stream`, { method: "POST",
    headers: { authorization: `Bearer ${serviceToken}`, "x-awp-owner": owner,
      "x-awp-turn-id": "protocol-voice", "x-awp-origin": "voice",
      "content-type": "application/json" }, body: JSON.stringify({ messages: [
    { role: "system", content: "Answer briefly." }, { role: "user", content: "spoken" },
  ] }) });
  const voiceStream = await voiceResponse.text();
  assert.equal(voiceResponse.status, 200);
  assert.match(voiceStream, /Pi heard spoken/u);
  assert.match(voiceStream, /"type":"done"/u);
  assert.equal(modelCalls, 2);
  const transcript = await call("GET", "/v1/transcript");
  assert.deepEqual(transcript.body.entries.map((entry) => entry.text),
    ["typed", "Pi heard typed", "uncertain", "spoken", "Pi heard spoken"]);
  const catalog = await call("GET", "/v1/sessions");
  assert.equal(catalog.body.sessions[0].id, selected.id);
  await agent.attachTask(taskId);
  const attached = await call("POST", "/v1/turn", { messages: [
    { role: "system", content: "Answer briefly." }, { role: "user", content: "task prompt" },
  ] }, { "x-awp-turn-id": "protocol-task", "x-awp-origin": "voice" });
  assert.equal(attached.status, 200);
  assert.equal(attached.body.text, "Task heard prompt");
  assert.deepEqual(taskPrompts, ["task prompt"]);
  assert.equal(modelCalls, 2);
  const attachedHistory = await call("GET", `/v1/sessions/${selected.id}`);
  assert.ok(!attachedHistory.body.entries.some((entry) => entry.text === "task prompt"));
  const fresh = await call("POST", "/v1/sessions/new", {},
    { "x-awp-command-id": "protocol-second" });
  assert.equal(fresh.status, 201);
  assert.notEqual(fresh.body.id, selected.id);
  assert.equal(agent.remoteTask.attachedTaskId, undefined);
  const history = await call("GET", `/v1/sessions/${selected.id}`);
  assert.deepEqual(history.body.entries.map((entry) => entry.text),
    ["typed", "Pi heard typed", "uncertain", "spoken", "Pi heard spoken"]);
  const resume = await call("POST", `/v1/sessions/${selected.id}/resume`, {},
    { "x-awp-command-id": "protocol-resume" });
  assert.equal(resume.body.id, selected.id);
  assert.deepEqual((await call("GET", "/v1/transcript")).body.entries,
    history.body.entries);
  const interrupted = new AbortController();
  const interruptedResponse = await fetch(`${base}/v1/turn/stream`, { method: "POST",
    signal: interrupted.signal,
    headers: { authorization: `Bearer ${serviceToken}`, "x-awp-owner": owner,
      "x-awp-turn-id": "protocol-interrupted", "x-awp-origin": "voice",
      "content-type": "application/json" }, body: JSON.stringify({ messages: [
    { role: "system", content: "Answer briefly." }, { role: "user", content: "slow" },
  ] }) });
  assert.equal(interruptedResponse.status, 200);
  let activeTurn;
  for (let attempt = 0; attempt < 40; attempt++) {
    activeTurn = await grainClient.call("turn", { "turn-id": "protocol-interrupted" })
      .catch(() => undefined);
    if (activeTurn?.status === "accepted" && modelCalls >= 3) break;
    await new Promise((resolveWait) => setTimeout(resolveWait, 100));
  }
  assert.equal(activeTurn?.status, "accepted");
  assert.equal(modelCalls, 3);
  const stop = await call("POST", "/v1/turn/abort", {});
  assert.equal(stop.status, 200);
  await interruptedResponse.body.cancel();
  interrupted.abort();
  let interruptedTurn;
  for (let attempt = 0; attempt < 40; attempt++) {
    interruptedTurn = await grainClient.call("turn", { "turn-id": "protocol-interrupted" })
      .catch(() => undefined);
    if (interruptedTurn?.status === "uncertain" || interruptedTurn?.status === "interrupted") break;
    await new Promise((resolveWait) => setTimeout(resolveWait, 100));
  }
  assert.ok(["uncertain", "interrupted"].includes(interruptedTurn?.status),
    JSON.stringify(interruptedTurn));
  grain.kill("SIGTERM");
  await new Promise((resolveExit) => grain.once("exit", resolveExit));
  grain = startGrain();
  await waitForGrain(grainPort, grain);
  assert.equal((await grainClient.call("selection")).id, selected.id);
  const recovered = (await call("GET", "/v1/transcript")).body.entries;
  assert.deepEqual(recovered.slice(0, history.body.entries.length), history.body.entries);
  assert.equal(recovered.at(-1).status, "uncertain");
  const unauthorized = await call("GET", "/v1/sessions", undefined,
    { authorization: "Bearer wrong" });
  assert.equal(unauthorized.status, 401);
  const foreign = await call("GET", "/v1/sessions", undefined,
    { "x-awp-owner": "foreign@example.test" });
  assert.equal(foreign.status, 403);
  assert.equal(toolEnvironment.identity, "one-voice-vm");
  await agent.attachTask(taskId);
  assert.equal((await grainClient.call("routing"))["task-id"], taskId);
  await agent.close();
  agentClosed = true;
  assert.equal((await grainClient.call("routing"))["task-id"], null);
});
