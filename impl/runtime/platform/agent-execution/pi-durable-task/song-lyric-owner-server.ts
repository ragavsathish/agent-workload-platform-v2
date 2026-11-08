import { createServer, type Server } from "node:http";
import { readFile, chmod } from "node:fs/promises";
import { timingSafeEqual } from "node:crypto";
import { Effect } from "effect";
import { openSongLyricOwner, type SongLyricOwner } from "./song-lyric-owner.js";

type Envelope = { id?: string; command?: string; requestId?: string; input?: unknown;
  taskId?: string; identity?: string };
const MAX_BYTES = 256_000;

function authorized(header: string | undefined, token: string): boolean {
  const value = header?.startsWith("Bearer ") ? header.slice(7) : "";
  const actual = Buffer.from(value);
  const expected = Buffer.from(token);
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}

export function serveSongLyricOwner(owner: SongLyricOwner, token: string, port: number): Server {
  const server = createServer(async (request, response) => {
    if (request.url === "/health" && request.method === "GET") {
      response.writeHead(200, { "content-type": "application/json" }).end('{"ready":true}');
      return;
    }
    if (request.url !== "/private/task" || request.method !== "POST") {
      response.writeHead(404).end(); return;
    }
    if (!authorized(request.headers.authorization, token)) {
      response.writeHead(403).end(); return;
    }
    let body = "";
    for await (const chunk of request) {
      body += chunk.toString();
      if (Buffer.byteLength(body) > MAX_BYTES) { response.writeHead(413).end(); return; }
    }
    try {
      const command = JSON.parse(body) as Envelope;
      let data: unknown;
      switch (command.command) {
        case "submit":
          if (typeof command.requestId !== "string") throw new Error("request identity required");
          data = await owner.submit(command.requestId, command.input);
          break;
        case "get":
          if (typeof command.taskId !== "string") throw new Error("task identity required");
          data = await owner.get(command.taskId);
          break;
        case "cancel_product":
          if (typeof command.taskId !== "string" || typeof command.identity !== "string") throw new Error("task and owner required");
          await owner.cancel(command.taskId, command.identity);
          data = { cancelled: true };
          break;
        case "abort":
          // The owner scoped cancellation command is the authority; an extra abort is idempotent.
          data = { disposition: "owner_cancelled" };
          break;
        case "retry":
          if (typeof command.taskId !== "string") throw new Error("task identity required");
          await owner.retry(command.taskId); data = { retried: true };
          break;
        default: throw new Error("unknown task command");
      }
      response.writeHead(200, { "content-type": "application/json" })
        .end(JSON.stringify({ id: command.id, success: true, data }));
    } catch (error) {
      response.writeHead(400, { "content-type": "application/json" })
        .end(JSON.stringify({ success: false, error: String(error) }));
    }
  });
  server.listen(port, "127.0.0.1");
  return server;
}

async function main(): Promise<void> {
  const storagePath = process.env.AWP_SONG_LYRIC_OWNER_DB;
  const tokenFile = process.env.AWP_PI_DURABLE_OWNER_TOKEN_FILE;
  const mapJson = process.env.AWP_WORKSPACE_MAP_JSON;
  const port = Number(process.env.AWP_PI_DURABLE_OWNER_PORT ?? "9451");
  if (!storagePath || !tokenFile || !mapJson || !Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("song lyric owner needs storage, token file, workspace map, and port");
  }
  const token = (await readFile(tokenFile, "utf8")).trim();
  if (!token) throw new Error("song lyric owner token is empty");
  const workspaceRoots = JSON.parse(mapJson) as Record<string, string>;
  const owner = await openSongLyricOwner({ storagePath, workspaceRoots });
  await chmod(storagePath, 0o600);
  const server = serveSongLyricOwner(owner, token, port);
  const stop = async () => {
    await new Promise<void>((resolve) => server.close(() => resolve()));
    await owner.close();
  };
  process.once("SIGTERM", () => { void Effect.runPromise(Effect.tryPromise(stop)).finally(() => process.exit()); });
  process.once("SIGINT", () => { void Effect.runPromise(Effect.tryPromise(stop)).finally(() => process.exit()); });
  process.stdout.write(`song lyric owner listening on 127.0.0.1:${port}\n`);
}

if (import.meta.url === new URL(`file://${process.argv[1]}`).href) await main();
