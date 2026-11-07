import { Effect } from "effect";
import { closeTaskSessions } from "./remote-session.js";
import { runPiSession } from "./session-execution.js";

const taskId = process.env.AWP_QUALIFICATION_TASK_ID;
const workspace = process.env.AWP_QUALIFICATION_TASK_WORKSPACE;
if (!taskId || !workspace) throw new Error("qualification task identity and workspace are required");

process.once("SIGTERM", () => {
  void Effect.runPromise(Effect.tryPromise(() => closeTaskSessions())).finally(() => process.exit());
});

await Effect.runPromise(Effect.tryPromise(() => runPiSession({
  taskId,
  workspace,
  instruction: "Reply briefly that this durable task is ready for follow-up.",
})));
process.stdout.write("durable task worker ready\n");
