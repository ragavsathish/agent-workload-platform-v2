# Slice 03 implementation boundary

This note implements the stack-free [slice plan](../docs/plan/03-session-orchestration/plan.md)
against the pinned [v1 product description](../raw/v1/voice-product-readme.md),
[owner edge](../raw/v1/edge-bot.py), and
[orchestrator](../raw/v1/voice-agent.ts).

Grain owns the current persistent orchestrator conversation's CQRS commands,
facts, and read models. Pipecat remains the library inside Voice Edge for the
live browser audio and turn pipeline. A voice turn reaches the same Grain
selection and turn boundary as a typed turn. The Pi service's persistent tool
environment and Gondolin voice VM remain separate from conversation identity;
the durable task runtime owns its own task identity and session.

The executable proof uses a controlled turn driver behind that boundary.
`awp-v2.session.api` exposes owner operations for fresh selection, resume,
list, historical read, active transcript, voice and typed turn admission,
stop, context, and task attachment. Grain commits facts with an owner-scoped
compare-and-swap and rebuilds the owner read model from those facts. A restart
marks accepted turns uncertain and releases a task attachment; it never
replays an external turn or assumes a task socket survived. The independent
restart probe runs create and read in separate processes.

This build proves orchestration through the public module, not a live
Pipecat, Pi, Gondolin, or durable-task adapter. It does not install the v1
HTTP routes or connect browser audio. The controlled driver supplies safe
assistant and tool observations and context usage. Context compaction updates
the bounded projection but does not compact provider state. Stop requests
need a production adapter to confirm downstream cancellation or reconcile
effects; an uncertain turn remains inspectable and is not automatically
retried. Tool environment ownership stays outside the conversation module.
