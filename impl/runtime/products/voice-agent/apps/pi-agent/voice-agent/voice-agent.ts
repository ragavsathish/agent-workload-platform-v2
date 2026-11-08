import { join } from "node:path";
import { randomUUID } from "node:crypto";
import { existsSync } from "node:fs";

import {
  calculateContextTokens,
  createAgentSession,
  createMcpExtension,
  createToolSearchExtension,
  DEFAULT_COMPACTION_SETTINGS,
  getLastAssistantUsage,
  ModelRuntime,
  SessionManager,
  type AgentSession,
} from "@earendil-works/pi-coding-agent";
import { GondolinSession } from "@agent-workload-platform/gondolin-session";

import { parseVoiceTurn, VOICE_TURN_ORIGIN_CUSTOM_TYPE } from "./conversation.js";
import {
  assistantText,
  createGrafanaMcpRegistration,
  createJournalReviewMcpRegistration,
  createPlaywrightTraceMcpRegistration,
  createRemoteTaskSessionRegistration,
  createWassetteMcpRegistration,
  isolatedVoiceMcpConfig,
  PLAYWRIGHT_TRACE_TOOLS,
  skillResourceLoader,
} from "./pi-session.js";
import {
  PublicTranscript,
  type PublicTranscriptEntry,
  type PublicTranscriptSnapshot,
  publicSessionTranscript,
  restorePublicTranscript,
} from "./public-transcript.js";
import { createSerialExecutor } from "./serial-executor.js";
import { createVoiceGondolinSession } from "./voice-gondolin.js";
import type { ServiceSettings } from "./voice-config.js";
import { type PublicSessionSummary } from "./voice-sessions.js";
import { RemoteTaskSession } from "./remote-task-session.js";
import { GrainSessionClient } from "./grain-client.js";
import { Effect } from "effect";

type GondolinTools = Awaited<ReturnType<GondolinSession["codingTools"]>>;
type VoiceAgentSettings = Pick<
  ServiceSettings,
  | "agentDir"
  | "cwd"
  | "grafanaMcpToken"
  | "grafanaMcpUrl"
  | "playwrightTraceArtifactRoot"
  | "playwrightTraceOutputRoot"
  | "playwrightTraceRenderer"
  | "playwrightTraceViewerBaseUrl"
  | "remoteTaskSocketDir"
  | "model"
  | "sessionDir"
  | "skillsDir"
  | "thinking"
  | "timeoutMs"
  | "wassetteBin"
  | "wassetteComponentDir"
  | "wassetteEnvFile"
>;

export type PublicContextUsage = {
  tokens: number | null;
  contextWindow: number | null;
  percent: number | null;
  estimated: boolean;
  canCompact: boolean;
  compactionCommandId: string | null;
  compactionStatus: "idle" | "queued" | "pending" | "completed" | "uncertain";
  compactionBaselineLeafId?: string | null;
  compactionBaselineEntryCount?: number | null;
};

type VoiceSessionState =
  | { kind: "unbound"; manager: SessionManager; systemPrompt: string }
  | {
    kind: "active";
    session: AgentSession;
    systemPrompt: string;
    unsubscribe: () => void;
  }
  | { kind: "closed" };

// Keep this as an additive selection. Passing plain tool names makes Pi treat
// the list as an allowlist; once any mcp__ name is present, every other MCP
// tool is discarded, including Wassette's deferred tools. Additive entries
// preserve the MCP registry while still making the voice tools available.
export const VOICE_INITIAL_TOOLS = [
  "+read",
  "+write",
  "+edit",
  "+bash",
  "+tool_search",
  "+mcp__grafana__search_tempo_traces",
  "+mcp__grafana__get_tempo_trace",
  ...PLAYWRIGHT_TRACE_TOOLS.map((name) => `+${name}`),
  "+mcp__journal_review__render_journal_review",
  "+mcp__journal_review__review_active",
  "+mcp__journal_review__navigate_review",
  "+mcp__journal_review__get_review_status",
  "+attach_task_session",
] as const;

export function canManuallyCompact(
  tokens: number | null,
  keepRecentTokens: number,
  compacting: boolean,
  lastEntryType: string | undefined,
): boolean {
  return tokens !== null
    && tokens > keepRecentTokens
    && !compacting
    && lastEntryType !== undefined
    && lastEntryType !== "compaction";
}

function runtimeSettings(settings: ServiceSettings): VoiceAgentSettings {
  return {
    agentDir: settings.agentDir,
    cwd: settings.cwd,
    grafanaMcpToken: settings.grafanaMcpToken,
    grafanaMcpUrl: settings.grafanaMcpUrl,
    playwrightTraceArtifactRoot: settings.playwrightTraceArtifactRoot,
    playwrightTraceOutputRoot: settings.playwrightTraceOutputRoot,
    playwrightTraceRenderer: settings.playwrightTraceRenderer,
    playwrightTraceViewerBaseUrl: settings.playwrightTraceViewerBaseUrl,
    remoteTaskSocketDir: settings.remoteTaskSocketDir,
    model: settings.model,
    sessionDir: settings.sessionDir,
    skillsDir: settings.skillsDir,
    thinking: settings.thinking,
    timeoutMs: settings.timeoutMs,
    wassetteBin: settings.wassetteBin,
    wassetteComponentDir: settings.wassetteComponentDir,
    wassetteEnvFile: settings.wassetteEnvFile,
  };
}

export class VoiceAgent {
  private sessionState: VoiceSessionState;
  private readonly transcript: PublicTranscript;
  private activeAssistantId?: string;
  private readonly operations = createSerialExecutor();
  private routingQueue: Promise<void> = Promise.resolve();
  private routingBusy = false;
  private activeDeltaSink?: (delta: string) => void;
  private estimatedContextTokens?: number;
  private compacting = false;
  private readonly remoteTask: RemoteTaskSession;
  private observationChain: Promise<unknown> = Promise.resolve();
  private activeTurnId?: string;
  private stopRequestedTurnId?: string;

  private constructor(
    private readonly settings: VoiceAgentSettings,
    private readonly modelRuntime: ModelRuntime,
    private readonly gondolin: GondolinSession,
    private readonly gondolinTools: GondolinTools,
    private persistedContextTokens: number | null,
    private persistedContextWindow: number | null,
    persistedSession: SessionManager,
    transcript: PublicTranscript,
    private readonly grain: GrainSessionClient,
  ) {
    this.sessionState = { kind: "unbound", manager: persistedSession, systemPrompt: "" };
    this.transcript = transcript;
    this.remoteTask = new RemoteTaskSession(settings.remoteTaskSocketDir, settings.timeoutMs);
  }

  static async create(settings: ServiceSettings): Promise<VoiceAgent> {
    const grain = new GrainSessionClient(
      settings.grainSessionUrl, settings.grainSessionToken, settings.ownerIdentity,
    );
    let selectedId: string;
    try {
      const selected = await grain.call("selection");
      selectedId = String(selected.id);
    } catch (error) {
      if (!(error instanceof Error) || error.message !== "not-found") throw error;
      const fresh = await grain.call("fresh", { "command-id": randomUUID() });
      selectedId = String(fresh.id);
    }
    const priorRouting = await grain.call("routing");
    if (priorRouting["task-id"]) await grain.call("voice-ended");
    const modelRuntime = await ModelRuntime.create({
      authPath: join(settings.agentDir, "auth.json"),
      modelsPath: join(settings.agentDir, "models.json"),
      modelsStorePath: join(settings.agentDir, "models-store.json"),
      allowModelNetwork: false,
    });
    const gondolin = await createVoiceGondolinSession({
      giteaEndpointUrl: settings.giteaEndpointUrl,
      giteaOwner: settings.giteaOwner,
      giteaRepository: settings.giteaRepository,
      giteaToken: settings.giteaToken,
      imagePath: settings.gondolinAssets,
      oxigraphUrl: settings.oxigraphUrl,
      prometheusEndpointUrl: settings.prometheusEndpointUrl,
      skillsDir: settings.skillsDir,
      s3MockAccessKey: settings.s3MockAccessKey,
      s3MockBucket: settings.s3MockBucket,
      s3MockEndpointUrl: settings.s3MockEndpointUrl,
      s3MockSecretKey: settings.s3MockSecretKey,
      sessionLabel: "pi-voice",
    });
    const gondolinTools = await gondolin.codingTools(settings.cwd);
    const selectedPath = SessionManager.findById(settings.cwd, selectedId, settings.sessionDir);
    if (!selectedPath) {
      const recorded = await grain.call("history", { "conversation-id": selectedId });
      if (Array.isArray(recorded.entries) && recorded.entries.length > 0) {
        await gondolin.close();
        throw new Error("recorded conversation has no Pi session file; inspect before recovery");
      }
    }
    const persistedSession = selectedPath
      ? SessionManager.open(selectedPath, settings.sessionDir, settings.cwd)
      : SessionManager.create(settings.cwd, settings.sessionDir, { id: selectedId });
    const entries = persistedSession.getEntries();
    const persistedUsage = getLastAssistantUsage(entries);
    const modelChange = [...entries].reverse().find((entry) => entry.type === "model_change");
    const persistedModel = modelChange?.type === "model_change"
      ? modelRuntime.getModel(modelChange.provider, modelChange.modelId)
      : undefined;
    return new VoiceAgent(
      runtimeSettings(settings),
      modelRuntime,
      gondolin,
      gondolinTools,
      persistedUsage ? calculateContextTokens(persistedUsage) : null,
      persistedModel?.contextWindow ?? null,
      persistedSession,
      restorePublicTranscript(persistedSession),
      grain,
    );
  }

  private currentManager(): SessionManager {
    if (this.sessionState.kind === "active") return this.sessionState.session.sessionManager;
    if (this.sessionState.kind === "unbound") return this.sessionState.manager;
    throw new Error("Pi voice agent is closed");
  }

  private withRoutingTransition<T>(operation: () => Promise<T>): Promise<T> {
    const result = this.routingQueue.then(async () => {
      this.routingBusy = true;
      try {
        return await operation();
      } finally {
        this.routingBusy = false;
      }
    });
    this.routingQueue = result.then(() => undefined, () => undefined);
    return result;
  }

  private observe(kind: string, observation: Record<string, unknown>): void {
    const turnId = this.activeTurnId;
    if (!turnId) return;
    this.observationChain = this.observationChain.then(() =>
      this.grain.call("observe", { "turn-id": turnId, observation: { kind, ...observation } }));
  }

  async attachTaskSession(taskId: string, commandId = randomUUID()): Promise<{ taskId: string; isStreaming: boolean }> {
    return this.withRoutingTransition(() => this.attachTaskSessionOnce(taskId, commandId));
  }

  private async attachTaskSessionOnce(taskId: string, commandId: string): Promise<{ taskId: string; isStreaming: boolean }> {
    if (this.operations.isBusy()) {
      throw new Error("Pi is busy; stop or finish the active turn before routing a task");
    }
    const remote = this.remoteTask;
    const grain = this.grain;
    const routing = await grain.call("routing");
    const recordedTaskId = typeof routing["task-id"] === "string" ? routing["task-id"] : undefined;
    const priorTaskId = remote.attachedTaskId;
    const priorCommand = await grain.call("routing-command", { "command-id": commandId });
    const priorOperation = priorCommand.operation;
    const priorCommandTaskId = typeof priorCommand["task-id"] === "string"
      ? priorCommand["task-id"] : undefined;
    if (priorOperation !== undefined && priorOperation !== null
      && (priorOperation !== "attach" || priorCommandTaskId !== taskId)) {
      throw new Error("task routing command identity conflicts with a prior input");
    }
    if (priorOperation === "attach") {
      if (recordedTaskId !== taskId || priorTaskId !== taskId) {
        throw new Error("task routing command completed earlier; current routing has changed");
      }
      const current = await remote.state();
      return { taskId, isStreaming: current.isStreaming };
    }
    if (recordedTaskId === taskId && priorTaskId === taskId) {
      // Ask Grain to validate the command receipt even for a no-op retry; a
      // reused command identity with different input must still conflict.
      await grain.call("attach", { "task-id": taskId, "command-id": commandId });
      const current = await remote.state();
      return { taskId, isStreaming: current.isStreaming };
    }
    if (recordedTaskId !== priorTaskId) {
      throw new Error("task routing is uncertain; inspect routing before attaching a task");
    }
    try {
      const state = await remote.attach(taskId);
      await grain.call("attach", { "task-id": taskId, "command-id": commandId });
      return state;
    } catch (error) {
      try {
        if (priorTaskId) await remote.attach(priorTaskId);
        else await remote.detach();
      } catch {
        // The route remains fail-closed: runTurn compares the Grain decision
        // with the live socket before dispatching any subsequent turn.
      }
      throw error;
    }
  }

  private attachTask(taskId: string): Promise<{ taskId: string; isStreaming: boolean }> {
    return this.attachTaskSession(taskId);
  }

  async taskRoutingSnapshot(): Promise<{ taskId: string | null }> {
    if (this.routingBusy) {
      throw new Error("task routing is uncertain while a route transition is in progress");
    }
    const routing = await this.grain.call("routing");
    const recordedTaskId = typeof routing["task-id"] === "string" ? routing["task-id"] : undefined;
    const liveTaskId = this.remoteTask.attachedTaskId;
    if (recordedTaskId !== liveTaskId) {
      throw new Error("task routing is uncertain; inspect before continuing");
    }
    return { taskId: recordedTaskId ?? null };
  }

  private activeSession(): AgentSession | undefined {
    return this.sessionState.kind === "active" ? this.sessionState.session : undefined;
  }

  private replaceWithUnbound(manager: SessionManager, systemPrompt = ""): void {
    if (this.sessionState.kind === "active") {
      this.sessionState.unsubscribe();
      this.sessionState.session.dispose();
    }
    this.sessionState = { kind: "unbound", manager, systemPrompt };
  }

  private async newSession(systemPrompt: string): Promise<void> {
    const sessionManager = this.currentManager();
    this.replaceWithUnbound(sessionManager, systemPrompt);
    const resourceLoader = await skillResourceLoader(systemPrompt, this.settings, [
      createToolSearchExtension(),
      createMcpExtension({
        loadConfig: isolatedVoiceMcpConfig,
        logPath: join(this.settings.agentDir, "mcp.log"),
      }),
      createWassetteMcpRegistration(this.settings),
      createGrafanaMcpRegistration(this.settings),
      createPlaywrightTraceMcpRegistration(this.settings),
      createJournalReviewMcpRegistration(),
      createRemoteTaskSessionRegistration({ attach: (taskId) => this.attachTask(taskId) }),
    ]);
    const model = this.settings.model
      ? this.modelRuntime.getModel(this.settings.model.provider, this.settings.model.modelId)
      : undefined;
    if (this.settings.model && !model) {
      throw new Error(`Pi voice model is unavailable: ${this.settings.model.source}`);
    }
    const { session } = await createAgentSession({
      cwd: this.settings.cwd,
      agentDir: this.settings.agentDir,
      modelRuntime: this.modelRuntime,
      model,
      thinkingLevel: this.settings.thinking,
      tools: [...VOICE_INITIAL_TOOLS],
      customTools: this.gondolinTools,
      resourceLoader,
      sessionManager,
    });
    const unsubscribe = session.subscribe((event) => {
      if (event.type === "message_start" && event.message.role === "assistant") {
        this.activeAssistantId = this.transcript.beginAssistant();
      } else if (
        event.type === "message_update"
        && event.assistantMessageEvent.type === "text_delta"
      ) {
        this.activeAssistantId ??= this.transcript.beginAssistant();
        this.transcript.appendAssistantText(
          this.activeAssistantId,
          event.assistantMessageEvent.delta,
        );
        this.activeDeltaSink?.(event.assistantMessageEvent.delta);
        this.observe("assistant-delta", { text: event.assistantMessageEvent.delta });
      } else if (event.type === "message_end" && event.message.role === "assistant") {
        if (this.activeAssistantId) this.transcript.finishAssistant(this.activeAssistantId);
        this.activeAssistantId = undefined;
        this.estimatedContextTokens = undefined;
        const usage = session.getContextUsage();
        if (usage?.tokens !== null && usage?.tokens !== undefined) {
          this.persistedContextTokens = usage.tokens;
          this.persistedContextWindow = usage.contextWindow;
        }
      } else if (event.type === "tool_execution_start") {
        this.transcript.startTool(event.toolCallId, event.toolName);
        this.observe("tool-start", { "tool-id": event.toolCallId, name: event.toolName });
      } else if (event.type === "tool_execution_end") {
        this.transcript.finishTool(event.toolCallId, event.isError);
        this.observe("tool-end", { "tool-id": event.toolCallId, "failed?": event.isError });
      }
    });
    this.sessionState = { kind: "active", session, systemPrompt, unsubscribe };
    await session.bindExtensions({});
  }

  async transcriptSnapshot(): Promise<PublicTranscriptSnapshot> {
    return await this.grain.call("transcript") as unknown as PublicTranscriptSnapshot;
  }

  async sessionCatalog(): Promise<{ sessions: PublicSessionSummary[] }> {
    return await this.grain.call("catalog") as { sessions: PublicSessionSummary[] };
  }

  async historicalSession(sessionId: string): Promise<PublicTranscriptSnapshot | undefined> {
    try {
      return await this.grain.call("history", { "conversation-id": sessionId }) as unknown as PublicTranscriptSnapshot;
    } catch (error) {
      if (error instanceof Error && error.message === "not-found") return undefined;
      throw error;
    }
  }

  async startFreshSession(commandId = randomUUID()): Promise<{ id: string }> {
    return this.withRoutingTransition(() => this.startFreshSessionOnce(commandId));
  }

  private async startFreshSessionOnce(commandId: string): Promise<{ id: string }> {
    if (this.operations.isBusy()) {
      throw new Error("Pi is busy; stop or finish the active turn before starting a new session");
    }
    const priorTaskId = this.remoteTask.attachedTaskId;
    await this.remoteTask.detach();
    let result: Record<string, unknown>;
    try {
      result = await this.grain.call("fresh", { "command-id": commandId });
    } catch (error) {
      if (priorTaskId) await this.remoteTask.attach(priorTaskId).catch(() => undefined);
      throw error;
    }
    const manager = SessionManager.create(this.settings.cwd, this.settings.sessionDir,
      { id: String(result.id) });
    this.replaceWithUnbound(manager);
    this.persistedContextTokens = null;
    this.persistedContextWindow = null;
    this.estimatedContextTokens = undefined;
    this.transcript.reset();
    return { id: String(result.id) };
  }

  async resumeSession(sessionId: string, commandId = randomUUID()): Promise<{ id: string } | undefined> {
    return this.withRoutingTransition(() => this.resumeSessionOnce(sessionId, commandId));
  }

  private async resumeSessionOnce(sessionId: string, commandId: string): Promise<{ id: string } | undefined> {
    if (this.operations.isBusy()) {
      throw new Error("Pi is busy; stop or finish the active turn before resuming a session");
    }
    let recorded: Record<string, unknown>;
    try {
      recorded = await this.grain.call("history", { "conversation-id": sessionId });
    } catch (error) {
      if (error instanceof Error && error.message === "not-found") return undefined;
      throw error;
    }
    const path = SessionManager.findById(this.settings.cwd, sessionId, this.settings.sessionDir);
    if (!path && Array.isArray(recorded.entries) && recorded.entries.length > 0) {
      throw new Error("recorded conversation has no Pi session file; inspect before recovery");
    }
    const manager = path
      ? SessionManager.open(path, this.settings.sessionDir, this.settings.cwd)
      : SessionManager.create(this.settings.cwd, this.settings.sessionDir, { id: sessionId });
    const priorTaskId = this.remoteTask.attachedTaskId;
    await this.remoteTask.detach();
    try {
      await this.grain.call("resume", { "command-id": commandId, "conversation-id": sessionId });
    } catch (error) {
      if (priorTaskId) await this.remoteTask.attach(priorTaskId).catch(() => undefined);
      throw error;
    }
    const entries = manager.getEntries();
    const usage = getLastAssistantUsage(entries);
    const modelChange = [...entries].reverse().find((entry) => entry.type === "model_change");
    const model = modelChange?.type === "model_change"
      ? this.modelRuntime.getModel(modelChange.provider, modelChange.modelId)
      : undefined;
    this.replaceWithUnbound(manager);
    this.persistedContextTokens = usage ? calculateContextTokens(usage) : null;
    this.persistedContextWindow = model?.contextWindow ?? null;
    this.estimatedContextTokens = undefined;
    this.transcript.replace(publicSessionTranscript(manager.getBranch()));
    return { id: manager.getSessionId() };
  }

  private piContextSnapshot(): PublicContextUsage {
    const session = this.activeSession();
    const usage = session?.getContextUsage();
    const tokens = usage?.tokens ?? this.estimatedContextTokens ?? this.persistedContextTokens;
    const contextWindow = usage?.contextWindow
      ?? session?.model?.contextWindow
      ?? this.persistedContextWindow;
    const percent = tokens !== null && contextWindow
      ? Math.min(100, (tokens / contextWindow) * 100)
      : null;
    const branch = this.currentManager().getBranch();
    const keepRecentTokens = session?.settingsManager.getCompactionKeepRecentTokens()
      ?? DEFAULT_COMPACTION_SETTINGS.keepRecentTokens;
    return {
      tokens,
      contextWindow,
      percent,
      estimated: usage?.tokens == null && this.estimatedContextTokens !== undefined,
      canCompact: canManuallyCompact(
        tokens,
        keepRecentTokens,
        this.compacting,
        branch.at(-1)?.type,
      ),
      compactionCommandId: null,
      compactionStatus: "idle",
    };
  }

  async contextSnapshot(): Promise<PublicContextUsage> {
    return await this.grain.call("context") as unknown as PublicContextUsage;
  }

  compact(commandId: string): Promise<PublicContextUsage> {
    if (this.activeTurnId) {
      return (async () => {
        const queued = await this.grain.call("compact-start", {
          "command-id": commandId,
        });
        if (queued.status !== "queued") {
          throw new Error("Grain did not queue context compaction");
        }
        // Grain owns the durable queue. runTurn resumes it after the active turn settles.
        return this.contextSnapshot();
      })();
    }
    return this.operations.run("maintenance", () => this.compactOnce(commandId));
  }

  private compactOnce(commandId: string): Promise<PublicContextUsage> {
    const agent = this;
    return Effect.runPromise(
      Effect.gen(function* () {
        if (!/^[A-Za-z0-9_-]{1,128}$/u.test(commandId)) {
          return yield* Effect.fail(new Error("invalid compaction command identity"));
        }
        if (agent.sessionState.kind === "closed") {
          return yield* Effect.fail(new Error("Pi voice agent is closed"));
        }
        if (agent.sessionState.kind === "unbound") {
          const systemPrompt = agent.sessionState.systemPrompt;
          yield* Effect.tryPromise(() => agent.newSession(systemPrompt));
        }

        const session = agent.activeSession();
        if (!session) return yield* Effect.fail(new Error("Pi voice session is unavailable"));
        const manager = session.sessionManager;
        const recorded = yield* Effect.tryPromise(() => agent.contextSnapshot());
        const pendingId = recorded.compactionCommandId;
        const hasPending = ["queued", "pending", "uncertain"].includes(recorded.compactionStatus);
        if (hasPending && pendingId !== commandId) {
          return yield* Effect.fail(new Error("another context compaction is pending"));
        }
        if (pendingId === commandId && recorded.compactionStatus === "completed") {
          return yield* Effect.tryPromise(() => agent.contextSnapshot());
        }
        if (!hasPending && !recorded.canCompact) {
          return yield* Effect.fail(new Error("selected conversation cannot be compacted"));
        }

        const isPendingRetry = pendingId === commandId
          && ["pending", "uncertain"].includes(recorded.compactionStatus);
        const baselineLeafId = isPendingRetry
          ? recorded.compactionBaselineLeafId
          : manager.getLeafId();
        const baselineEntryCount = isPendingRetry
          ? recorded.compactionBaselineEntryCount
          : manager.getEntryCount();
        if (!baselineLeafId || typeof baselineEntryCount !== "number"
            || !Number.isInteger(baselineEntryCount) || baselineEntryCount <= 0) {
          return yield* Effect.fail(new Error("Pi session has no compaction cursor"));
        }
        const started = yield* Effect.tryPromise(() => agent.grain.call("compact-start", {
          "command-id": commandId,
          "baseline-leaf-id": baselineLeafId,
          "baseline-entry-count": baselineEntryCount,
        }));
        if (started.status === "queued") {
          return yield* Effect.tryPromise(() => agent.contextSnapshot());
        }
        if (started.status === "completed") {
          return yield* Effect.tryPromise(() => agent.contextSnapshot());
        }
        if (started.status !== "pending" && started.status !== "uncertain") {
          return yield* Effect.fail(new Error("Grain did not accept context compaction"));
        }

        const proveApplied = () => {
          const branch = manager.getBranch();
          const tail = branch.at(-1);
          return tail?.type === "compaction"
            && tail.parentId === baselineLeafId
            && manager.getEntryCount() > baselineEntryCount
            ? tail
            : undefined;
        };
        const settle = (entry: NonNullable<ReturnType<typeof proveApplied>>) =>
          agent.grain.call("compact-complete", {
            "command-id": commandId,
            "entry-id": entry.id,
            "parent-id": entry.parentId,
            "entry-count": manager.getEntryCount(),
            tokens: agent.persistedContextTokens,
            contextWindow: session.model?.contextWindow ?? null,
          });
        const markUncertain = (reason: "pi-outcome-unproven" | "pi-effect-failed") =>
          agent.grain.call("compact-uncertain", { "command-id": commandId, reason });

        const observed = proveApplied();
        if (observed) {
          yield* Effect.tryPromise(() => settle(observed));
          return yield* Effect.tryPromise(() => agent.contextSnapshot());
        }
        if (started["effect-claim?"] !== true) {
          yield* Effect.tryPromise(() => markUncertain("pi-outcome-unproven"));
          return yield* Effect.fail(new Error(
            "context compaction is pending but the Pi branch does not prove its outcome"));
        }
        if (manager.getLeafId() !== baselineLeafId
            || manager.getEntryCount() !== baselineEntryCount) {
          yield* Effect.tryPromise(() => markUncertain("pi-outcome-unproven"));
          return yield* Effect.fail(new Error(
            "Pi session changed after Grain recorded the compaction cursor"));
        }

        agent.compacting = true;
        try {
          const compactResult = yield* Effect.either(
            Effect.tryPromise(() => session.compact()),
          );
          if (compactResult._tag === "Left") {
            const error = compactResult.left;
            const entry = proveApplied();
            if (entry) {
              const settlement = yield* Effect.either(Effect.tryPromise(() => settle(entry)));
              if (settlement._tag === "Right") {
                return yield* Effect.tryPromise(() => agent.contextSnapshot());
              }
              yield* Effect.tryPromise(() => markUncertain("pi-outcome-unproven"));
              return yield* Effect.fail(error);
            }
            yield* Effect.tryPromise(() => markUncertain("pi-effect-failed"));
            return yield* Effect.fail(error);
          }
          const result = compactResult.right;
          agent.estimatedContextTokens = result.estimatedTokensAfter;
          agent.persistedContextTokens = result.estimatedTokensAfter ?? null;
          agent.persistedContextWindow = session.model?.contextWindow ?? null;
          const entry = proveApplied();
          if (!entry) {
            yield* Effect.tryPromise(() => markUncertain("pi-outcome-unproven"));
            return yield* Effect.fail(new Error(
              "Pi compaction returned without the expected branch entry"));
          }
          const settlement = yield* Effect.either(Effect.tryPromise(() => settle(entry)));
          if (settlement._tag === "Left") {
            yield* Effect.tryPromise(() => markUncertain("pi-outcome-unproven"));
            return yield* Effect.fail(settlement.left);
          }
          return yield* Effect.tryPromise(() => agent.contextSnapshot());
        } finally {
          agent.compacting = false;
        }
      }),
    );
  }

  private executeTurn(rawMessages: unknown, onDelta?: (delta: string) => void): Promise<string> {
    return this.operations.run("turn", async () => {
      const { systemPrompt, prompt, origin } = parseVoiceTurn(rawMessages);
      if (this.remoteTask.attachedTaskId) {
        this.transcript.addUser(prompt);
        const assistantId = this.transcript.beginAssistant();
        let streamed = "";
        try {
          const text = await this.remoteTask.send(prompt, (delta) => {
            streamed += delta;
            this.transcript.appendAssistantText(assistantId, delta);
            this.observe("assistant-delta", { text: delta });
            onDelta?.(delta);
          });
          if (text.startsWith(streamed) && text.length > streamed.length) {
            const remainder = text.slice(streamed.length);
            this.transcript.appendAssistantText(assistantId, remainder);
            this.observe("assistant-delta", { text: remainder });
            onDelta?.(remainder);
          }
          return text;
        } finally {
          this.transcript.finishAssistant(assistantId);
        }
      }
      if (this.sessionState.kind !== "active" || this.sessionState.systemPrompt !== systemPrompt) {
        await this.newSession(systemPrompt);
      }
      const session = this.activeSession();
      if (!session) throw new Error("Pi voice session is unavailable");

      this.transcript.addUser(prompt);
      this.activeDeltaSink = onDelta;
      let timedOut = false;
      const timeout = setTimeout(() => {
        timedOut = true;
        void session.abort();
      }, this.settings.timeoutMs);
      try {
        session.sessionManager.appendCustomEntry(VOICE_TURN_ORIGIN_CUSTOM_TYPE, { origin });
        await session.prompt(prompt, { expandPromptTemplates: false, source: "rpc" });
        const qualificationHold = process.env.VOICE_QUALIFICATION_HOLD_TURN_FILE;
        if (qualificationHold && existsSync(qualificationHold)) {
          await Effect.runPromise(Effect.sleep(8_000));
        }
        await session.waitForIdle();
      } finally {
        clearTimeout(timeout);
        this.activeDeltaSink = undefined;
      }
      if (timedOut) throw new Error("Pi voice turn timed out");
      return assistantText(session);
    });
  }

  private async runTurn(rawMessages: unknown, turnId: string,
    origin: "voice" | "typed", onDelta?: (delta: string) => void): Promise<string> {
    if (this.routingBusy) throw new Error("task routing is changing; inspect routing and retry");
    const parsed = parseVoiceTurn(rawMessages);
    const decision = await this.grain.call("submit", {
      "turn-id": turnId, origin, messages: rawMessages,
    });
    if (decision.reused === true || decision["reused?"] === true) {
      if (decision.status === "completed") {
        const prior = await this.grain.call("turn", { "turn-id": turnId });
        return String(prior.answer ?? "");
      }
      throw new Error(`turn ${String(decision.status)}; inspect before retry`);
    }
    if (decision["conversation-id"] !== this.currentManager().getSessionId()) {
      await this.grain.call("settle", { "turn-id": turnId, status: "uncertain" });
      throw new Error("selected conversation context is not bound");
    }
    if ((decision["task-id"] ?? null) !== (this.remoteTask.attachedTaskId ?? null)) {
      await this.grain.call("settle", { "turn-id": turnId, status: "uncertain" });
      throw new Error("task routing is not bound");
    }
    this.activeTurnId = turnId;
    this.stopRequestedTurnId = undefined;
    this.observationChain = Promise.resolve();
    try {
      const answer = await this.executeTurn(rawMessages, onDelta);
      await this.observationChain;
      if (this.stopRequestedTurnId === turnId) {
        throw new Error("Pi voice turn was stopped; inspect its uncertain outcome");
      }
      if (!this.remoteTask.attachedTaskId) {
        const context = this.piContextSnapshot();
        await this.grain.call("observe", { "turn-id": turnId, observation: {
          kind: "context-usage", tokens: context.tokens,
          contextWindow: context.contextWindow, estimated: context.estimated,
          canCompact: context.canCompact,
        } });
      }
      await this.grain.call("settle", { "turn-id": turnId, status: "completed", answer });
      return answer;
    } catch (error) {
      await this.grain.call("settle", { "turn-id": turnId, status: "uncertain" });
      throw error;
    } finally {
      this.activeTurnId = undefined;
      this.stopRequestedTurnId = undefined;
      void this.resumeQueuedCompactionAfterTurn();
    }
  }

  private async resumeQueuedCompactionAfterTurn(): Promise<void> {
    try {
      const context = await this.contextSnapshot();
      const commandId = context.compactionStatus === "queued"
        ? context.compactionCommandId
        : null;
      if (commandId) {
        void this.operations.run("maintenance", () => this.compactOnce(commandId)).catch(() => undefined);
      }
    } catch {
      // The durable queued command remains visible; a client retry can resume it.
    }
  }

  turn(rawMessages: unknown, turnId = randomUUID(), origin: "voice" | "typed" = "voice"): Promise<string> {
    return this.runTurn(rawMessages, turnId, origin);
  }

  streamTurn(rawMessages: unknown, onDelta: (delta: string) => void,
    turnId = randomUUID(), origin: "voice" | "typed" = "voice"): Promise<string> {
    return this.runTurn(rawMessages, turnId, origin, onDelta);
  }

  async abort(): Promise<void> {
    if (this.activeTurnId) {
      this.stopRequestedTurnId = this.activeTurnId;
      try {
        await this.grain.call("stop", { "turn-id": this.activeTurnId });
      } catch (error) {
        if (!(error instanceof Error) || error.message !== "not-found") throw error;
      }
    }
    if (this.remoteTask.attachedTaskId) await this.remoteTask.abort();
    else await this.activeSession()?.abort();
  }

  async detachTaskSession(commandId = randomUUID()): Promise<void> {
    return this.withRoutingTransition(() => this.detachTaskSessionOnce(commandId));
  }

  private async detachTaskSessionOnce(commandId: string): Promise<void> {
    if (this.operations.isBusy()) {
      throw new Error("Pi is busy; stop or finish the active turn before detaching a task");
    }
    const priorTaskId = this.remoteTask.attachedTaskId;
    const routing = await this.grain.call("routing");
    const recordedTaskId = typeof routing["task-id"] === "string" ? routing["task-id"] : undefined;
    const priorCommand = await this.grain.call("routing-command", { "command-id": commandId });
    const priorOperation = priorCommand.operation;
    if (priorOperation !== undefined && priorOperation !== null
      && priorOperation !== "detach") {
      throw new Error("task routing command identity conflicts with a prior input");
    }
    if (priorOperation === "detach") {
      if (recordedTaskId !== undefined || priorTaskId !== undefined) {
        throw new Error("task routing command completed earlier; current routing has changed");
      }
      return;
    }
    await this.remoteTask.detach();
    try {
      await this.grain.call("detach", { "command-id": commandId });
    } catch (error) {
      if (priorTaskId) await this.remoteTask.attach(priorTaskId).catch(() => undefined);
      throw error;
    }
  }

  async close(): Promise<void> {
    await this.routingQueue;
    const session = this.activeSession();
    if (this.sessionState.kind === "active") this.sessionState.unsubscribe();
    this.sessionState = { kind: "closed" };
    if (session) {
      await session.abort().catch(() => undefined);
      session.dispose();
    }
    await this.remoteTask.detach();
    await this.grain.call("voice-ended").catch(() => undefined);
    await this.gondolin.close();
  }
}
