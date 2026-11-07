# Pi Durable task conversation

This package ports the v1 Pi Durable conversation, Gondolin execution environment, MCP tool adapter, and remote session socket into v2. The v2 voice service uses the socket to route an attached owner's spoken turn into the task's own persistent Pi Durable conversation. The task's follow-up prompt is not copied into the orchestrator's saved conversation.

`qualification-worker.ts` starts one real Gondolin-backed worker for an eligible Grain task seeded by `awp-v2.qualify.task-seed`. It is a foreground qualification fixture, not a deployed owner scheduler. The local launcher supplies a fresh task ID, isolated workspace and session paths, and the existing host Pi model credential. The worker records only SHA-256 hashes of settled follow-up prompts and answers in a private evidence file when `VOICE_QUALIFICATION_TASK_EVIDENCE_FILE` is configured.

The imported source came from the sibling v1 `platform/agent-execution/pi-durable-task` package. V2 does not yet run v1's owner server, CLI, lease scheduler, or task UI. A deployed task service must start and supervise this worker from Grain task lifecycle decisions and retain its task socket and SQLite store across service restart.
