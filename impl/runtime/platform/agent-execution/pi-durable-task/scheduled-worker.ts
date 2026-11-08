import { Effect } from "effect";
import { runPiSession } from "./session-execution.js";

const endpoint = process.env.AWP_GRAIN_SESSION_URL;
const token = process.env.AWP_GRAIN_SESSION_TOKEN;
const taskId = process.env.AWP_SCHEDULED_TASK_ID;
const workspace = process.env.AWP_SCHEDULED_TASK_WORKSPACE;
const instruction = process.env.AWP_SCHEDULED_TASK_INSTRUCTION;
const imageRef = process.env.AWP_SCHEDULED_TASK_IMAGE_REF || undefined;
const giteaRepository = process.env.AWP_SCHEDULED_TASK_GITEA_REPOSITORY || undefined;
if (!endpoint || !token || !taskId || !workspace || !instruction) {
  throw new Error("scheduled worker requires Grain endpoint, token, task, workspace, and instruction");
}

const post = (operation: string, body: object): Effect.Effect<void, Error> => Effect.tryPromise({
  try: async () => {
    const response = await fetch(`${endpoint}/internal/task/${operation}`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(10_000),
    });
    if (!response.ok) throw new Error(`Grain task ${operation} returned ${response.status}`);
  },
  catch: (error) => new Error(String(error)),
});

const run = Effect.gen(function* () {
  let progressPosts: Promise<void> = Promise.resolve();
  const result = yield* Effect.tryPromise({
    try: () => runPiSession({ taskId, workspace, instruction, imageRef, giteaRepository,
      onProgress: (summary) => {
        progressPosts = progressPosts.then(() => Effect.runPromise(post("progress", { "task-id": taskId, summary })))
          .catch(() => {});
      },
    }),
    catch: (error) => new Error(String(error)),
  });
  yield* Effect.tryPromise({ try: () => progressPosts, catch: (error) => new Error(String(error)) });
  yield* post("capture-complete", { "task-id": taskId, response: { text: result.slice(0, 12_000) } });
});

try {
  await Effect.runPromise(run);
} catch (error) {
  await Effect.runPromise(post("fail", { "task-id": taskId, failure: "Execution failed; inspect private worker diagnostics." })).catch(() => {});
  process.stderr.write(`scheduled task failed: ${String(error)}\n`);
  process.exitCode = 1;
}
