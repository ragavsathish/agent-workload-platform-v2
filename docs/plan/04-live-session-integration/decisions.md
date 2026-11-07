# Slice decisions and unresolved evidence

Cites the [slice plan](plan.md), [live conversation rules](../../wiki/live-session-integration.md), [slice 03 decisions](../03-session-orchestration/decisions.md), and [pinned sources](../../../raw/v1/manifest.md).

## D13: recorded selection is authoritative for live routing

The [source orchestrator](../../../raw/v1/voice-agent.ts) owns a saved conversation manager while the previous slice records an independent selection. This slice requires the selected identity, opened context, and dispatched destination to agree before a public success. A failed context bind becomes a bounded failure or inspectable recovery state. The pruned branch was two independent selections that could diverge after restart or a lost reply.

## D14: one external dispatch for an accepted turn

The [source broker](../../../raw/v1/broker-http.py) and [service routes](../../../raw/v1/voice-http-server.ts) do not carry a caller turn identity. The live path propagates a stable identity from owner ingress to dispatch and observation. The [slice 03 uncertainty rule](../03-session-orchestration/decisions.md) applies when an answer is lost: inspect the accepted turn, do not resubmit the prompt. The pruned branch was treating a transport retry as a new model turn.

## D15: keep the existing owner contract and separate authorities

The [owner edge](../../../raw/v1/edge-bot.py) authenticates the browser and the [service routes](../../../raw/v1/voice-http-server.ts) require an inner credential. Preserve the existing public path names and response shapes while adding stable command identity through a compatible internal envelope. Outer owner identity must be resolved before commands or reads; inner credentials do not grant owner authority. The pruned branch was exposing the inner service as the owner API.

## D16: service lifetime owns the tool environment

The [service entry point](../../../raw/v1/voice-agent-service.ts) creates one orchestrator, and the [orchestrator](../../../raw/v1/voice-agent.ts) creates one persistent voice tool environment before opening or switching saved conversations. Selection and voice connection lifecycle must not create another tool environment. A process restart may replace the environment as part of a new service lifetime. The pruned branch was one environment per conversation or browser connection.

## D17: task attachment is temporary routing

The [task capability](../../../raw/v1/pi-session.ts) attaches an eligible task socket; [task transport](../../../raw/v1/remote-task-session.ts) sends and aborts against that task conversation. Selection and browser disconnect release it. The task owns its identity and history. On service restart, report detached routing rather than reconstruct a live socket from a prior fact. The pruned branch was copying task turns into orchestrator saved history.

## Open: atomicity across recorded facts and conversation effects

The [source orchestrator](../../../raw/v1/voice-agent.ts) persists its own conversation history. The live build must define and test reconciliation when a recorded turn is accepted but the external conversation prompt, stream, or settlement fails. Recorded facts remain the decision authority; a partial external effect becomes uncertain and is never blindly repeated. The exact persistence coupling belongs in the [implementation note](../../../impl/live-session-integration-notes.md).

## Open: deployed speech and model availability

The [source voice pipeline](../../../raw/v1/edge-bot.py) uses speech and model providers outside the conversation boundary. Protocol tests may control these outer providers while running the real public routing and conversation lifecycle. Deployed verification requires the configured services and is a separate gate.
