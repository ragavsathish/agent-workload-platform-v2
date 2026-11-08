import {
  createServer,
  type IncomingMessage,
  type Server,
  type ServerResponse,
} from "node:http";

import type { PublicContextUsage } from "./voice-agent.js";
import type { PublicTranscriptSnapshot } from "./public-transcript.js";
import type { PublicSessionSummary } from "./voice-sessions.js";
import type { NarrationRequest, TaskNarrator } from "./task-narrator.js";

type VoiceAgentPort = {
  transcriptSnapshot(): Promise<PublicTranscriptSnapshot>;
  sessionCatalog(): Promise<{ sessions: PublicSessionSummary[] }>;
  historicalSession(sessionId: string): Promise<PublicTranscriptSnapshot | undefined>;
  startFreshSession(commandId?: string): Promise<{ id: string }>;
  resumeSession(sessionId: string, commandId?: string): Promise<{ id: string } | undefined>;
  contextSnapshot(): Promise<PublicContextUsage>;
  compact(commandId: string): Promise<PublicContextUsage>;
  abort(): Promise<void>;
  attachTaskSession(taskId: string, commandId?: string): Promise<{ taskId: string; isStreaming: boolean }>;
  taskRoutingSnapshot(): Promise<{ taskId: string | null }>;
  detachTaskSession(commandId?: string): Promise<void>;
  turn(messages: unknown, turnId?: string, origin?: "voice" | "typed"): Promise<string>;
  streamTurn(messages: unknown, onDelta: (delta: string) => void,
    turnId?: string, origin?: "voice" | "typed"): Promise<string>;
};

type TelemetryPort = {
  observe<T>(name: string, operation: () => Promise<T>): Promise<T>;
};

type VoiceRoute =
  | { kind: "health" }
  | { kind: "transcript" }
  | { kind: "sessions" }
  | { kind: "new-session" }
  | { kind: "resume-session"; sessionId: string }
  | { kind: "historical-session"; sessionId: string }
  | { kind: "context" }
  | { kind: "compact" }
  | { kind: "abort" }
  | { kind: "attach-task" }
  | { kind: "task-routing" }
  | { kind: "detach-task" }
  | { kind: "task-narration" }
  | { kind: "turn"; stream: boolean }
  | { kind: "not-found" };

const MAX_BODY_BYTES = 131_072;

export function matchVoiceRoute(method = "", url = ""): VoiceRoute {
  if (method === "GET" && url === "/health") return { kind: "health" };
  if (method === "GET" && url === "/v1/transcript") return { kind: "transcript" };
  if (method === "GET" && url === "/v1/sessions") return { kind: "sessions" };
  if (method === "POST" && url === "/v1/sessions/new") return { kind: "new-session" };
  if (method === "GET" && url === "/v1/context") return { kind: "context" };
  if (method === "POST" && url === "/v1/context/compact") return { kind: "compact" };
  if (method === "POST" && url === "/v1/turn/abort") return { kind: "abort" };
  if (method === "POST" && url === "/v1/tasks/attach") return { kind: "attach-task" };
  if (method === "GET" && url === "/v1/tasks/routing") return { kind: "task-routing" };
  if (method === "POST" && url === "/v1/tasks/detach") return { kind: "detach-task" };
  if (method === "POST" && url === "/v1/tasks/narration") return { kind: "task-narration" };
  if (method === "POST" && (url === "/v1/turn" || url === "/v1/turn/stream")) {
    return { kind: "turn", stream: url.endsWith("/stream") };
  }
  const resume = url.match(/^\/v1\/sessions\/([A-Za-z0-9_-]{1,128})\/resume$/);
  if (method === "POST" && resume) return { kind: "resume-session", sessionId: resume[1] };
  const historical = url.match(/^\/v1\/sessions\/([A-Za-z0-9_-]{1,128})$/);
  if (method === "GET" && historical) {
    return { kind: "historical-session", sessionId: historical[1] };
  }
  return { kind: "not-found" };
}

async function readBody(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const value = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += value.length;
    if (size > MAX_BODY_BYTES) throw new Error("request body is too large");
    chunks.push(value);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

export function parseNarrationRequest(body: unknown): NarrationRequest {
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new Error("invalid narration request");
  const value = body as Record<string, unknown>;
  if (
    typeof value.taskId !== "string"
    || !/^[0-9a-f-]{36}$/u.test(value.taskId)
    || typeof value.instruction !== "string"
    || value.instruction.length > 1_500
    || !Array.isArray(value.events)
    || value.events.length < 1
    || value.events.length > 40
    || !value.events.every((event) => typeof event === "string" && event.length > 0 && event.length <= 250)
  ) throw new Error("invalid narration request");
  return value as NarrationRequest;
}

function sendJson(
  response: ServerResponse,
  status: number,
  payload: unknown,
): void {
  const body = JSON.stringify(payload);
  response.writeHead(status, {
    "content-type": "application/json",
    "content-length": Buffer.byteLength(body),
  });
  response.end(body);
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

export function createVoiceHttpServer(options: {
  agent: VoiceAgentPort;
  narrator: Pick<TaskNarrator, "summarize">;
  serviceToken: string;
  ownerIdentity: string;
  telemetry: TelemetryPort;
}): Server {
  const { agent, narrator, serviceToken, ownerIdentity, telemetry } = options;
  return createServer(async (request, response) => {
    if (request.headers.authorization !== `Bearer ${serviceToken}`) {
      sendJson(response, 401, { detail: "invalid Pi voice credential" });
      return;
    }
    if (request.headers["x-awp-owner"] !== ownerIdentity) {
      sendJson(response, 403, { detail: "owner identity mismatch" });
      return;
    }

    const route = matchVoiceRoute(request.method, request.url);
    if (route.kind === "health") return sendJson(response, 200, { status: "ok" });
    if (route.kind === "not-found") return sendJson(response, 404, { detail: "not found" });
    response.setHeader("cache-control", "no-store");

    if (route.kind === "transcript") return sendJson(response, 200, await agent.transcriptSnapshot());
    if (route.kind === "sessions") return sendJson(response, 200, await agent.sessionCatalog());
    if (route.kind === "historical-session") {
      const snapshot = await agent.historicalSession(route.sessionId);
      return snapshot
        ? sendJson(response, 200, snapshot)
        : sendJson(response, 404, { detail: "session not found" });
    }
    if (route.kind === "context") return sendJson(response, 200, await agent.contextSnapshot());
    if (route.kind === "abort") {
      await agent.abort();
      return sendJson(response, 200, { status: "stopped" });
    }
    if (route.kind === "task-routing") {
      try {
        return sendJson(response, 200, await agent.taskRoutingSnapshot());
      } catch (error) {
        return sendJson(response, 409, {
          detail: errorMessage(error, "Task routing is uncertain"),
        });
      }
    }
    if (route.kind === "attach-task") {
      try {
        const body = await readBody(request);
        const taskId = body && typeof body === "object" && !Array.isArray(body)
          ? (body as Record<string, unknown>).taskId : undefined;
        const commandId = request.headers["x-awp-command-id"];
        if (typeof taskId !== "string" || taskId.length > 128
          || (commandId !== undefined && typeof commandId !== "string")) {
          return sendJson(response, 400, { detail: "invalid task attachment request" });
        }
        const result = await agent.attachTaskSession(taskId, commandId);
        return sendJson(response, 200, result);
      } catch (error) {
        return sendJson(response, 409, {
          detail: errorMessage(error, "Pi task could not be attached"),
        });
      }
    }
    if (route.kind === "detach-task") {
      try {
        const commandId = request.headers["x-awp-command-id"];
        if (commandId !== undefined && typeof commandId !== "string") {
          return sendJson(response, 400, { detail: "invalid task detach request" });
        }
        await agent.detachTaskSession(commandId);
        return sendJson(response, 200, { status: "detached" });
      } catch (error) {
        return sendJson(response, 409, {
          detail: errorMessage(error, "Pi task could not be detached"),
        });
      }
    }
    if (route.kind === "task-narration") {
      try {
        const body = parseNarrationRequest(await readBody(request));
        const spoken = await telemetry.observe("pi.task-narration", () => narrator.summarize(body));
        return sendJson(response, 200, { spoken });
      } catch (error) {
        return sendJson(response, 502, {
          detail: errorMessage(error, "Pi task narration failed"),
        });
      }
    }
    if (route.kind === "new-session" || route.kind === "resume-session") {
      try {
        const result = route.kind === "new-session"
          ? await agent.startFreshSession(request.headers["x-awp-command-id"] as string | undefined)
          : await agent.resumeSession(route.sessionId, request.headers["x-awp-command-id"] as string | undefined);
        if (!result) return sendJson(response, 404, { detail: "session not found" });
        return sendJson(response, route.kind === "new-session" ? 201 : 200, result);
      } catch (error) {
        return sendJson(response, 409, {
          detail: errorMessage(error, "Pi session could not be changed"),
        });
      }
    }
    if (route.kind === "compact") {
      try {
        const commandId = request.headers["x-awp-command-id"];
        return sendJson(response, 200,
          await agent.compact(typeof commandId === "string" ? commandId : ""));
      } catch (error) {
        return sendJson(response, 409, {
          detail: errorMessage(error, "Pi context compaction failed"),
        });
      }
    }

    try {
      const body = await readBody(request);
      if (!body || typeof body !== "object" || Array.isArray(body)) {
        throw new Error("invalid request");
      }
      const messages = (body as Record<string, unknown>).messages;
      const turnId = request.headers["x-awp-turn-id"] as string | undefined;
      const origin = request.headers["x-awp-origin"] === "typed" ? "typed" : "voice";
      if (!route.stream) {
        const text = await telemetry.observe("pi.turn", () => agent.turn(messages, turnId, origin));
        return sendJson(response, 200, { text });
      }

      response.writeHead(200, {
        "cache-control": "no-cache, no-transform",
        "connection": "keep-alive",
        "content-type": "text/event-stream; charset=utf-8",
        "x-accel-buffering": "no",
      });
      const send = (event: Record<string, unknown>) => {
        response.write(`data: ${JSON.stringify(event)}\n\n`);
      };
      const abortOnDisconnect = () => {
        if (!response.writableEnded) void agent.abort();
      };
      response.once("close", abortOnDisconnect);
      try {
        await telemetry.observe(
          "pi.turn",
          () => agent.streamTurn(messages, (delta) => send({ type: "delta", delta }), turnId, origin),
        );
        send({ type: "done" });
      } catch (error) {
        send({ type: "error", detail: errorMessage(error, "Pi voice turn failed") });
      } finally {
        response.off("close", abortOnDisconnect);
        response.end();
      }
    } catch (error) {
      const detail = errorMessage(error, "Pi voice turn failed");
      if (!response.headersSent) sendJson(response, 502, { detail });
      else response.end();
    }
  });
}

export function listenVoiceHttpServer(server: Server, port: number, host: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const onError = (error: Error) => reject(error);
    server.once("error", onError);
    server.listen(port, host, () => {
      server.off("error", onError);
      resolve();
    });
  });
}

export function closeVoiceHttpServer(server: Server): Promise<void> {
  return new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
}
