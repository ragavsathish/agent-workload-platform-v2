# Ticket 02: live typed and spoken turns

Cites the [slice plan](plan.md), [scenarios](spec.feature), [voice pipeline source](../../../raw/v1/edge-bot.py), [broker](../../../raw/v1/broker-http.py), [service route](../../../raw/v1/voice-http-server.ts), and [orchestrator](../../../raw/v1/voice-agent.ts).

Carry one stable turn identity and immutable destination through typed and spoken ingress. Dispatch only the final actionable prompt. Observe safe assistant deltas and final outcome; do not expose private reasoning or tool payloads. Stop and stream disconnect request interruption at the actual destination. An uncertain outcome remains readable and the same identity cannot dispatch again. Prove `live-002`, `live-003`, `live-007`, and the invalid-turn portion of `live-008` through the production route and conversation path.
