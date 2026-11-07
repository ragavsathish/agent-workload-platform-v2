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

The first executable proof uses a controlled turn driver behind that boundary.
Its public module contract mirrors v1 owner operations for fresh selection,
resume, list, historical read, and active transcript. Network and audio
adapters can call the same module later without moving voice transport into
the conversation aggregate.
